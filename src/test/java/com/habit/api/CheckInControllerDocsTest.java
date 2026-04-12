package com.habit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.habit.domain.habit.CheckIn;
import com.habit.domain.habit.CheckInService;
import com.habit.domain.habit.Mood;
import com.habit.domain.habit.Streak;
import com.habit.infra.security.JwtAuthenticationFilter;
import com.habit.infra.security.RateLimitFilter;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(RestDocumentationExtension.class)
@WebMvcTest(
    controllers = CheckInController.class,
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
class CheckInControllerDocsTest {

    private static final Authentication AUTH_AS_42 =
        new UsernamePasswordAuthenticationToken(42L, null, List.of());

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private CheckInService checkInService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
    }

    @Test
    void create_checkin() throws Exception {
        CheckIn ci = CheckIn.create(10L, 42L, LocalDate.of(2026, 4, 13), "오늘도 해냈다", Mood.GOOD);
        setField(ci, "id", 1234L);
        Streak streak = Streak.initFor(10L);
        streak.overwrite(7, 21, 54, LocalDate.of(2026, 4, 13));
        CheckInService.CheckInResult result = new CheckInService.CheckInResult(ci, streak);
        given(checkInService.checkIn(any(), any(), any(), any(), any())).willReturn(result);

        String body = """
            {
              "checkedDate": "2026-04-13",
              "note": "오늘도 해냈다",
              "mood": "GOOD"
            }
            """;

        mockMvc.perform(post("/api/v1/habits/{habitId}/check-ins", 10L)
                .with(authentication(AUTH_AS_42))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andDo(document("checkin-create",
                pathParameters(
                    parameterWithName("habitId").description("체크인 대상 습관 ID. 소유자만 호출 가능.")
                ),
                requestFields(
                    fieldWithPath("checkedDate").description("체크인 날짜 (ISO). 생략시 UTC 오늘.").optional(),
                    fieldWithPath("note").description("~500자 메모. null 가능.").optional(),
                    fieldWithPath("mood").description("`GREAT`/`GOOD`/`OKAY`/`BAD`. null 가능.").optional()
                ),
                responseFields(
                    fieldWithPath("checkIn.id").description("생성된 체크인 ID."),
                    fieldWithPath("checkIn.habitId").description("습관 ID."),
                    fieldWithPath("checkIn.checkedDate").description("체크인 날짜."),
                    fieldWithPath("checkIn.note").description("저장된 메모.").optional(),
                    fieldWithPath("checkIn.mood").description("저장된 기분.").optional(),
                    fieldWithPath("streak.currentStreak").description("이 체크인 반영 후의 현재 연속 일수."),
                    fieldWithPath("streak.longestStreak").description("기록된 최장 연속 일수."),
                    fieldWithPath("streak.totalDays").description("누적 체크인 총 일수."),
                    fieldWithPath("streak.lastCheckedDate").description("마지막 체크인 날짜 (= 방금 체크인한 날짜).")
                )
            ));
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
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
