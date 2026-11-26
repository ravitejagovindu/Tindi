package com.tindi.api.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "recipes")
public class Recipe {
    @Id
    private String id;
    @Column(name = "user_id")
    private String userId;
    private String name;
    private String ingredients;
    private String steps;
    /** JSON list parallel to {@code steps}, or null. */
    @Column(name = "step_details")
    private String stepDetails;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    private SourceType sourceType;
    @Column(name = "source_url")
    private String sourceUrl;
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_categories", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "category_id")
    @Fetch(FetchMode.SUBSELECT)
    private Set<String> categoryIds = new HashSet<>();

    protected Recipe() {}

    public Recipe(String id, String userId, Instant now) {
        this.id = id;
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getIngredients() { return ingredients; }
    public void setIngredients(String v) { this.ingredients = v; }
    public String getSteps() { return steps; }
    public void setSteps(String v) { this.steps = v; }
    public String getStepDetails() { return stepDetails; }
    public void setStepDetails(String v) { this.stepDetails = v; }
    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType v) { this.sourceType = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String v) { this.thumbnailUrl = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
    public Set<String> getCategoryIds() { return categoryIds; }
}
