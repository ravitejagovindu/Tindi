package com.tindi.api.service;

import com.tindi.api.domain.Repositories.UserRepository;
import com.tindi.api.domain.User;
import com.tindi.api.dto.AuthDtos.AuthResponse;
import com.tindi.api.dto.AuthDtos.LoginRequest;
import com.tindi.api.dto.AuthDtos.MeResponse;
import com.tindi.api.dto.AuthDtos.SignupRequest;
import com.tindi.api.dto.AuthDtos.UserDto;
import com.tindi.api.domain.Repositories.CategoryRepository;
import com.tindi.api.domain.Repositories.RecipeRepository;
import com.tindi.api.security.TokenService;
import com.tindi.api.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final RecipeRepository recipes;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final SampleSeeder seeder;
    private final boolean seedSamples;
    private final String dummyHash;

    public AuthService(UserRepository users, CategoryRepository categories, RecipeRepository recipes,
                       PasswordEncoder encoder, TokenService tokens, SampleSeeder seeder,
                       @Value("${tindi.seed-sample-recipes:false}") boolean seedSamples) {
        this.users = users;
        this.categories = categories;
        this.recipes = recipes;
        this.encoder = encoder;
        this.tokens = tokens;
        this.seeder = seeder;
        this.seedSamples = seedSamples;
        this.dummyHash = encoder.encode("tindi-dummy-password");
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.validation("password", "Use 6 to 72 characters");
        }
        String email = req.email().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) throw emailTaken();
        User user = new User(UUID.randomUUID().toString(), req.name(), email,
                encoder.encode(req.password()), Instant.now().truncatedTo(ChronoUnit.MICROS));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        if (seedSamples) seeder.seed(user.getId());
        return new AuthResponse(tokens.issue(user.getId()), toDto(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().toLowerCase(Locale.ROOT)).orElse(null);
        // Always run one BCrypt check so timing does not reveal whether the email exists.
        boolean ok = encoder.matches(req.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Email or password is incorrect", null);
        }
        return new AuthResponse(tokens.issue(user.getId()), toDto(user));
    }

    @Transactional(readOnly = true)
    public MeResponse me(String userId) {
        User user = users.findById(userId).orElseThrow(ApiException::notFound);
        return new MeResponse(user.getId(), user.getName(), user.getEmail(),
                recipes.countByUserId(userId), categories.countByUserId(userId));
    }

    private static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail());
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "That email is already registered",
                Map.of("email", "This email is already in use"));
    }
}
