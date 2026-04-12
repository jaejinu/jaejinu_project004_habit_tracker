package com.habit.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private int anonymousPerHour = 60;
    private int authenticatedPerHour = 1000;

    public int getAnonymousPerHour() {
        return anonymousPerHour;
    }

    public void setAnonymousPerHour(int anonymousPerHour) {
        this.anonymousPerHour = anonymousPerHour;
    }

    public int getAuthenticatedPerHour() {
        return authenticatedPerHour;
    }

    public void setAuthenticatedPerHour(int authenticatedPerHour) {
        this.authenticatedPerHour = authenticatedPerHour;
    }
}
