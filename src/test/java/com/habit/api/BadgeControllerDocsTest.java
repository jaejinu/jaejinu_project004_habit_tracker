package com.habit.api;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.habit.domain.badge.PublicBadgeService;
import com.habit.infra.security.JwtAuthenticationFilter;
import com.habit.infra.security.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(RestDocumentationExtension.class)
@WebMvcTest(
    controllers = BadgeController.class,
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
class BadgeControllerDocsTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockBean
    private PublicBadgeService publicBadgeService;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.mockMvc = webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
    }

    @Test
    void calendar_svg_returns_200_with_cache_headers() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"772\" height=\"128\"></svg>";
        given(publicBadgeService.renderCalendar(anyString(), anyMap())).willReturn(svg);

        mockMvc.perform(get("/api/v1/badges/{token}/calendar.svg", "aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                "public, max-age=3600, s-maxage=3600, stale-while-revalidate=600"))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andDo(document("badge-calendar",
                responseHeaders(
                    headerWithName(HttpHeaders.CACHE_CONTROL).description("항상 `public, max-age=3600, s-maxage=3600, stale-while-revalidate=600`"),
                    headerWithName(HttpHeaders.ETAG).description("SVG 본문의 SHA-1 첫 16자 헥스값 (따옴표 감싸짐)"),
                    headerWithName(HttpHeaders.CONTENT_TYPE).description("`image/svg+xml;charset=utf-8`"),
                    headerWithName(HttpHeaders.VARY).description("`Accept-Encoding`")
                )
            ));
    }

    @Test
    void calendar_svg_returns_304_when_etag_matches() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>";
        given(publicBadgeService.renderCalendar(anyString(), anyMap())).willReturn(svg);

        String etag = mockMvc.perform(get("/api/v1/badges/{token}/calendar.svg", "aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG"))
            .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/v1/badges/{token}/calendar.svg", "aB3kL9mN2pQ5rS7tU0vW-xYzA1bC2dE3fG")
                .header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isNotModified())
            .andExpect(header().string(HttpHeaders.ETAG, etag))
            .andDo(document("badge-calendar-304"));
    }
}
