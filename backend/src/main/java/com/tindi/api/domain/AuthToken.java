package com.tindi.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "auth_tokens")
public class AuthToken {
    @Id
    private String id;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected AuthToken() {}

    public AuthToken(String id, String userId, String tokenHash, Instant now) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
}
