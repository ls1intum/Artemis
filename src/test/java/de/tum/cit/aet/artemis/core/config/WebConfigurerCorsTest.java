package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebConfigurerCorsTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void exposesPreparationPendingHeaderForConfiguredCrossOriginClients(boolean originPattern) throws Exception {
        var properties = new ArtemisProperties();
        var cors = properties.getCors();
        if (originPattern) {
            cors.setAllowedOriginPatterns(List.of("https://*.example.org"));
        }
        else {
            cors.setAllowedOrigins(List.of("https://client.example.org"));
        }
        cors.setAllowedMethods(List.of("POST"));
        cors.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.LINK, "X-Total-Count"));
        var configurer = new WebConfigurer(new MockEnvironment(), properties, null, null, null);
        var request = new MockHttpServletRequest("POST", "/api/exam/courses/1/exams/2/students");
        request.addHeader(HttpHeaders.ORIGIN, "https://client.example.org");
        var response = new MockHttpServletResponse();

        configurer.corsFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> response.setHeader("X-Artemis-Preparation-Pending", "true"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("https://client.example.org");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)).contains(HttpHeaders.AUTHORIZATION, HttpHeaders.LINK, "X-Total-Count",
                "X-Artemis-Preparation-Pending");
        assertThat(response.getHeader("X-Artemis-Preparation-Pending")).isEqualTo("true");
    }
}
