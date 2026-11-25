package com.tindi.api.security;

import com.tindi.api.domain.AuthToken;
import com.tindi.api.domain.Repositories.AuthTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private final SecureRandom random = new SecureRandom();
    private final AuthTokenRepository tokens;

    public TokenService(AuthTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** Creates a token for the user and returns the raw value. Only its hash is stored. */
    @Transactional
    public String issue(String userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        tokens.save(new AuthToken(UUID.randomUUID().toString(), userId, hash(raw), now));
        return raw;
    }

    /** Looks the token up, records last use, and returns who it belongs to. Tokens never expire. */
    @Transactional
    public Optional<AuthUser> authenticate(String raw) {
        return tokens.findByTokenHash(hash(raw)).map(t -> {
            tokens.touch(t.getId(), Instant.now().truncatedTo(ChronoUnit.MICROS));
            return new AuthUser(t.getUserId(), t.getId());
        });
    }

    @Transactional
    public void revoke(String tokenId) {
        tokens.deleteById(tokenId);
    }

    static String hash(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
