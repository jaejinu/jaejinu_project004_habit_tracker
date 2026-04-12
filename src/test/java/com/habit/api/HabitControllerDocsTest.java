package com.habit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import org.springframework.restdocs.payload.JsonFieldType;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.habit.domain.habit.Habit;
import com.habit.domain.habit.HabitFrequency;
import com.habit.domain.habit.HabitService;
import com.habit.domain.habit.Streak;
import com.habit.domain.habit.StreakRepository;
import com.habit.infra.security.JwtAuthenticationFilter;
import com.habit.infra.security.RateLimitFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    controllers = HabitController.class,
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
class HabitControllerDocsTest {

    private static final Authentication AUTH_AS_42 =
        new UsernamePasswordAuthenticationToken(42L, null, List.of());

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private HabitService habitService;

    @MockBean
    private StreakRepository streakRepository;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
    }

    @Test
    void list_my_habits() throws Exception {
        Habit h = Habit.create(42L, "매일 코딩", HabitFrequency.DAILY, LocalDate.of(2026, 1, 1));
        setField(h, "id", 10L);
        given(habitService.listMine(any())).willReturn(List.of(h));
        given(streakRepository.findByHabitId(10L)).willReturn(Optional.of(makeStreak(10L, 7, 21, 54)));

        mockMvc.perform(get("/api/v1/habits").with(authentication(AUTH_AS_42)))
            .andExpect(status().isOk())
            .andDo(document("habit-list",
                responseFields(
                    fieldWithPath("[].id").description("습관 ID."),
                    fieldWithPath("[].title").description("제목."),
                    fieldWithPath("[].description").type(JsonFieldType.STRING).description("부가 설명. nullable.").optional(),
                    fieldWithPath("[].color").description("잔디 색상 (`#RRGGBB`)."),
                    fieldWithPath("[].icon").type(JsonFieldType.STRING).description("이모지 1~16자. nullable.").optional(),
                    fieldWithPath("[].frequency").description("`DAILY` / `WEEKLY` / `CUSTOM`."),
                    fieldWithPath("[].targetDays").type(JsonFieldType.NUMBER).description("`WEEKLY` 때의 요일 비트마스크. nullable.").optional(),
                    fieldWithPath("[].isPublic").description("공개 여부. 공개면 `/api/v1/public/{publicId}/habits` 에 노출됨."),
                    fieldWithPath("[].startDate").description("목표 기간 시작일."),
                    fieldWithPath("[].endDate").type(JsonFieldType.STRING).description("종료일. nullable.").optional(),
                    fieldWithPath("[].currentStreak").description("현재 연속 달성 일수."),
                    fieldWithPath("[].longestStreak").description("기록된 최장 연속 달성."),
                    fieldWithPath("[].totalDays").description("총 체크인 수.")
                )
            ));
    }

    @Test
    void create_habit() throws Exception {
        Habit created = Habit.create(42L, "매일 코딩", HabitFrequency.DAILY, LocalDate.of(2026, 4, 13));
        setField(created, "id", 11L);
        given(habitService.create(any(), anyString(), any(), any())).willReturn(created);
        given(habitService.updateBasic(any(), any(), anyString(), any(), any(), any())).willReturn(created);
        given(habitService.updateSchedule(any(), any(), any(), any())).willReturn(created);
        given(streakRepository.findByHabitId(11L)).willReturn(Optional.of(makeStreak(11L, 0, 0, 0)));

        String body = """
            {
              "title": "매일 코딩",
              "description": "1시간 이상",
              "color": "#216e39",
              "icon": "💻",
              "frequency": "DAILY",
              "targetDays": null,
              "startDate": "2026-04-13",
              "endDate": null
            }
            """;

        mockMvc.perform(post("/api/v1/habits")
                .with(authentication(AUTH_AS_42))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andDo(document("habit-create",
                requestFields(
                    fieldWithPath("title").description("1~100자."),
                    fieldWithPath("description").type(JsonFieldType.STRING).description("~500자. null 가능.").optional(),
                    fieldWithPath("color").type(JsonFieldType.STRING).description("`#RRGGBB`. 생략시 `#216e39`.").optional(),
                    fieldWithPath("icon").type(JsonFieldType.STRING).description("이모지. null 가능.").optional(),
                    fieldWithPath("frequency").description("`DAILY`/`WEEKLY`/`CUSTOM`. 필수."),
                    fieldWithPath("targetDays").type(JsonFieldType.NUMBER).description("`WEEKLY`용 요일 비트마스크. null 가능.").optional(),
                    fieldWithPath("startDate").description("목표 시작일. 필수."),
                    fieldWithPath("endDate").type(JsonFieldType.STRING).description("종료일. null 가능.").optional()
                ),
                responseFields(
                    fieldWithPath("id").description("생성된 습관 ID. `Location` 헤더와 동일 값 포함."),
                    fieldWithPath("title").description("저장된 제목."),
                    fieldWithPath("description").type(JsonFieldType.STRING).description("저장된 설명.").optional(),
                    fieldWithPath("color").description("저장된 색상."),
                    fieldWithPath("icon").type(JsonFieldType.STRING).description("저장된 아이콘.").optional(),
                    fieldWithPath("frequency").description("주기."),
                    fieldWithPath("targetDays").type(JsonFieldType.NUMBER).description("요일 마스크.").optional(),
                    fieldWithPath("isPublic").description("초기값은 `false`."),
                    fieldWithPath("startDate").description("시작일."),
                    fieldWithPath("endDate").type(JsonFieldType.STRING).description("종료일.").optional(),
                    fieldWithPath("currentStreak").description("0 (방금 생성)."),
                    fieldWithPath("longestStreak").description("0."),
                    fieldWithPath("totalDays").description("0.")
                )
            ));
    }

    @Test
    void delete_habit() throws Exception {
        mockMvc.perform(delete("/api/v1/habits/{habitId}", 11L)
                .with(authentication(AUTH_AS_42)))
            .andExpect(status().isNoContent())
            .andDo(document("habit-delete",
                pathParameters(
                    parameterWithName("habitId").description("삭제할 습관 ID. 소유자만 호출 가능하며 관련된 체크인/스트릭/통계/뱃지가 단일 트랜잭션으로 함께 삭제됨.")
                )
            ));
    }

    private static Streak makeStreak(long habitId, int current, int longest, int total) {
        Streak s = Streak.initFor(habitId);
        s.overwrite(current, longest, total, null);
        return s;
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
