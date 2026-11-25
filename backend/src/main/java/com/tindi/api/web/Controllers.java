package com.tindi.api.web;

import com.tindi.api.dto.AuthDtos.AuthResponse;
import com.tindi.api.dto.AuthDtos.LoginRequest;
import com.tindi.api.dto.AuthDtos.MeResponse;
import com.tindi.api.dto.AuthDtos.SignupRequest;
import com.tindi.api.dto.CategoryDtos.CategoryDto;
import com.tindi.api.dto.CategoryDtos.CategoryRequest;
import com.tindi.api.dto.RecipeDtos.RecipeDto;
import com.tindi.api.dto.RecipeDtos.RecipeRequest;
import com.tindi.api.dto.SourceDtos.FetchRequest;
import com.tindi.api.dto.SourceDtos.FetchResponse;
import com.tindi.api.security.AuthUser;
import com.tindi.api.security.TokenService;
import com.tindi.api.service.AuthService;
import com.tindi.api.service.CategoryService;
import com.tindi.api.service.RecipeService;
import com.tindi.api.service.SourceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

public final class Controllers {
    private Controllers() {}

    @RestController
    @RequestMapping("/api/v1")
    public static class HealthAndMe {
        private final AuthService auth;

        public HealthAndMe(AuthService auth) {
            this.auth = auth;
        }

        @GetMapping("/health")
        public Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping("/me")
        public MeResponse me(@AuthenticationPrincipal AuthUser user) {
            return auth.me(user.userId());
        }
    }

    @RestController
    @RequestMapping("/api/v1/auth")
    public static class Auth {
        private final AuthService auth;
        private final TokenService tokens;

        public Auth(AuthService auth, TokenService tokens) {
            this.auth = auth;
            this.tokens = tokens;
        }

        @PostMapping("/signup")
        @ResponseStatus(HttpStatus.CREATED)
        public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
            return auth.signup(req);
        }

        @PostMapping("/login")
        public AuthResponse login(@Valid @RequestBody LoginRequest req) {
            return auth.login(req);
        }

        @PostMapping("/logout")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void logout(@AuthenticationPrincipal AuthUser user) {
            tokens.revoke(user.tokenId());
        }
    }

    @RestController
    @RequestMapping("/api/v1/categories")
    public static class Categories {
        private final CategoryService service;

        public Categories(CategoryService service) {
            this.service = service;
        }

        @GetMapping
        public List<CategoryDto> list(@AuthenticationPrincipal AuthUser user) {
            return service.list(user.userId());
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public CategoryDto create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CategoryRequest req) {
            return service.create(user.userId(), req.name());
        }

        @PatchMapping("/{id}")
        public CategoryDto rename(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                  @Valid @RequestBody CategoryRequest req) {
            return service.rename(user.userId(), id, req.name());
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
            service.delete(user.userId(), id);
        }
    }

    @RestController
    @RequestMapping("/api/v1/recipes")
    public static class Recipes {
        private final RecipeService service;

        public Recipes(RecipeService service) {
            this.service = service;
        }

        @GetMapping
        public List<RecipeDto> list(@AuthenticationPrincipal AuthUser user) {
            return service.list(user.userId());
        }

        @GetMapping("/{id}")
        public RecipeDto get(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
            return service.get(user.userId(), id);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public RecipeDto create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody RecipeRequest req) {
            return service.create(user.userId(), req);
        }

        @PutMapping("/{id}")
        public RecipeDto replace(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                 @Valid @RequestBody RecipeRequest req) {
            return service.replace(user.userId(), id, req);
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
            service.delete(user.userId(), id);
        }
    }

    @RestController
    @RequestMapping("/api/v1/source")
    public static class Source {
        private final SourceService service;

        public Source(SourceService service) {
            this.service = service;
        }

        @PostMapping("/fetch")
        public FetchResponse fetch(@Valid @RequestBody FetchRequest req) {
            return service.fetch(req.url());
        }
    }
}
