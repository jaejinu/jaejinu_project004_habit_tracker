package com.habit.domain.user;

import com.habit.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
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
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_public_id", columnNames = "public_id")
    }
)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @Column(name = "github_username", length = 100)
    private String githubUsername;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    public static User create(String email, String nickname) {
        return User.builder()
            .email(email)
            .nickname(nickname)
            .publicId(UUID.randomUUID())
            .build();
    }

    public static User createWithPassword(String email, String nickname, String passwordHash) {
        return User.builder()
            .email(email)
            .nickname(nickname)
            .publicId(UUID.randomUUID())
            .passwordHash(passwordHash)
            .build();
    }

    public void linkGithub(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
