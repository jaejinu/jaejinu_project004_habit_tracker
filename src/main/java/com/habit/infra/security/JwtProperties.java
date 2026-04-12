package com.habit.infra.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String secret = "change-me-to-a-long-random-value-at-least-256-bits-for-hs256";
    private Duration accessTokenTtl = Duration.ofHours(1);
    private Duration refreshTokenTtl = Duration.ofDays(14);

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }

    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
}
