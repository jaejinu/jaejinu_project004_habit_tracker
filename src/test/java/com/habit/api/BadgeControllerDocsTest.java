package com.habit.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.habit.domain.badge.PublicBadgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sample integration test for BadgeController — demonstrates the shape of a future
 * Spring REST Docs snapshot test. Full asciidoc generation is deferred (see
 * docs/design/step5-docs.md "Surface 3"). When wired, replace the plain MockMvc
 * assertions with `.andDo(document("badge-calendar", ...))` calls.
 */
@WebMvcTest(BadgeController.class)
@Import(BadgeControllerDocsTest.DisableSecurity.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "app.scheduler.enabled=false"
})
class BadgeControllerDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicBadgeService publicBadgeService;

    @Test
    void calendar_svg_returns_200_with_cache_headers() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"772\" height=\"128\"></svg>";
        given(publicBadgeService.renderCalendar(anyString(), anyMap())).willReturn(svg);

        mockMvc.perform(get("/api/v1/badges/test-token/calendar.svg"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                "public, max-age=3600, s-maxage=3600, stale-while-revalidate=600"))
            .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void calendar_svg_returns_304_when_etag_matches() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>";
        given(publicBadgeService.renderCalendar(anyString(), anyMap())).willReturn(svg);

        String etag = mockMvc.perform(get("/api/v1/badges/test-token/calendar.svg"))
            .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/v1/badges/test-token/calendar.svg")
                .header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isNotModified())
            .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @TestConfiguration
    static class DisableSecurity {
        // WebMvcTest only boots the controller slice; security autoconfig is excluded above.
    }
}
