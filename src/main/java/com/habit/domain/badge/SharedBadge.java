package com.habit.domain.badge;

import com.habit.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(
        name = "shared_badges",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_shared_badges_token", columnNames = "public_token")
        },
        indexes = {
                @Index(name = "ix_shared_badges_habit", columnList = "habit_id")
        }
)
public class SharedBadge extends BaseTimeEntity {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "habit_id", nullable = false)
    private Long habitId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false)
    private BadgeType badgeType;

    @Column(name = "public_token", nullable = false, length = 32)
    private String publicToken;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    public static SharedBadge create(Long habitId, Long userId, BadgeType type) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        if (encoded.length() > 32) {
            encoded = encoded.substring(0, 32);
        }
        return SharedBadge.builder()
                .habitId(habitId)
                .userId(userId)
                .badgeType(type)
                .publicToken(encoded)
                .viewCount(0L)
                .build();
    }

    public void recordView() {
        this.viewCount += 1;
    }
}
