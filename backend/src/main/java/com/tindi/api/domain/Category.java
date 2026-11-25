package com.tindi.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    private String id;
    @Column(name = "user_id")
    private String userId;
    private String name;
    private boolean starter;
    @Column(name = "sort_order")
    private int sortOrder;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Category() {}

    public Category(String id, String userId, String name, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.createdAt = createdAt;
        this.starter = false;
        this.sortOrder = 0;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isStarter() { return starter; }
    public int getSortOrder() { return sortOrder; }
}
