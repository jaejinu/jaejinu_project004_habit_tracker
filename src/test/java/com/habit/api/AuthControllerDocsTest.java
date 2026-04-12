package com.habit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.habit.domain.auth.AuthService;
import com.habit.domain.user.User;
import com.habit.infra.security.JwtAuthenticationFilter;
import com.habit.infra.security.RateLimitFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(RestDocumentationExtension.class)
@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtAuthenticationFilter.class, RateLimitFilter.class}
    )
)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "app.scheduler.enabled=false"
})
class AuthControllerDocsTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
    }

    @Test
    void signup_returns_user_summary() throws Exception {
        User user = User.createWithPassword("alice@example.com", "alice", "hashed");
        setId(user, 42L);
        UUID publicId = UUID.fromString("3f9e1c2a-0000-0000-0000-000000000000");
        setField(user, "publicId", publicId);

        given(authService.signup(anyString(), anyString(), anyString())).willReturn(user);

        String body = """
            {
              "email": "alice@example.com",
              "nickname": "alice",
              "password": "correct horse battery staple"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andDo(document("auth-signup",
                requestFields(
                    fieldWithPath("email").description("가입 이메일. 유효 형식이어야 함."),
                    fieldWithPath("nickname").description("1~50자의 표시 이름."),
                    fieldWithPath("password").description("8자 이상의 원문 비밀번호. 서버에서 BCrypt 해시 후 저장.")
                ),
                responseFields(
                    fieldWithPath("userId").description("내부 사용자 ID (URL 노출 금지)."),
                    fieldWithPath("publicId").description("공개 URL에서 사용하는 UUID (`/api/v1/public/{publicId}/...`)."),
                    fieldWithPath("email").description("가입한 이메일."),
                    fieldWithPath("nickname").description("표시 이름.")
                )
            ));
    }

    @Test
    void login_returns_bearer_token() throws Exception {
        AuthDtos.LoginResponse resp = new AuthDtos.LoginResponse("eyJhbGciOiJIUzI1NiJ9.payload.sig", "Bearer", 3600L);
        given(authService.login(anyString(), anyString())).willReturn(resp);

        String body = """
            {
              "email": "alice@example.com",
              "password": "correct horse battery staple"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andDo(document("auth-login",
                requestFields(
                    fieldWithPath("email").description("가입한 이메일."),
                    fieldWithPath("password").description("원문 비밀번호.")
                ),
                responseFields(
                    fieldWithPath("accessToken").description("JWT 액세스 토큰. `Authorization: Bearer <token>` 헤더로 이후 요청에 첨부."),
                    fieldWithPath("tokenType").description("항상 `Bearer`."),
                    fieldWithPath("expiresIn").description("토큰 만료까지 남은 초.")
                )
            ));
    }

    private static void setId(User user, long id) {
        setField(user, "id", id);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
