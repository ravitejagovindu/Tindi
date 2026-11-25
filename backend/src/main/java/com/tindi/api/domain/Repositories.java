package com.tindi.api.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class Repositories {
    private Repositories() {}

    public interface UserRepository extends JpaRepository<User, String> {
        Optional<User> findByEmail(String email);
        boolean existsByEmail(String email);
    }

    public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
        Optional<AuthToken> findByTokenHash(String tokenHash);

        @Modifying
        @Query("update AuthToken t set t.lastUsedAt = :now where t.id = :id")
        void touch(@Param("id") String id, @Param("now") Instant now);
    }

    public interface CategoryRepository extends JpaRepository<Category, String> {
        @Query("select c from Category c where c.userId is null or c.userId = :userId")
        List<Category> findVisible(@Param("userId") String userId);

        List<Category> findByUserId(String userId);
        long countByUserId(String userId);
        List<Category> findByUserIdIsNullOrderBySortOrder();
    }

    public interface RecipeRepository extends JpaRepository<Recipe, String> {
        List<Recipe> findByUserIdOrderByCreatedAtDescIdAsc(String userId);
        Optional<Recipe> findByIdAndUserId(String id, String userId);
        long countByUserId(String userId);
    }
}
