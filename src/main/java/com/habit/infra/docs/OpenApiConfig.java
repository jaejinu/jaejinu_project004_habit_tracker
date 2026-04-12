package com.habit.infra.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI habitTrackerOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Habit Tracker API")
                .version("v1")
                .description("GitHub 잔디 스타일 습관 트래커. 자세한 사용법은 docs/badge-guide.md 를 참고하세요.")
                .contact(new Contact().name("habit-tracker").url("https://github.com/"))
                .license(new License().name("MIT")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("local dev"),
                new Server().url("https://api.habittracker.io").description("prod (placeholder)")
            ))
            .components(new Components()
                .addSecuritySchemes(BEARER, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("`POST /api/v1/auth/login` 응답으로 받은 accessToken 을 `Bearer <token>` 형태로 전달합니다.")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
