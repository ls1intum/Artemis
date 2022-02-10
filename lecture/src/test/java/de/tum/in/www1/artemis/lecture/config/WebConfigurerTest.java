package de.tum.in.www1.artemis.lecture.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;

import javax.servlet.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.JHipsterProperties;

/**
 * Unit tests for the {@link WebConfigurer} class.
 */
class WebConfigurerTest {

    private WebConfigurer webConfigurer;

    private MockServletContext servletContext;

    private MockEnvironment env;

    private JHipsterProperties props;

    @BeforeEach
    public void setup() {
        servletContext = spy(new MockServletContext());
        doReturn(mock(FilterRegistration.Dynamic.class)).when(servletContext).addFilter(anyString(), any(Filter.class));
        doReturn(mock(ServletRegistration.Dynamic.class)).when(servletContext).addServlet(anyString(), any(Servlet.class));

        env = new MockEnvironment();
        props = new JHipsterProperties();

        webConfigurer = new WebConfigurer(env, props);
    }

    @Test
    void shouldStartUpProdServletContext() {
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_PRODUCTION);

        assertThatCode(() -> webConfigurer.onStartup(servletContext)).doesNotThrowAnyException();
    }

    @Test
    void shouldStartUpDevServletContext() throws ServletException {
        env.setActiveProfiles(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);

        assertThatCode(() -> webConfigurer.onStartup(servletContext)).doesNotThrowAnyException();
    }

    @Test
    void shouldCorsFilterOnApiPath() throws Exception {
        props.getCors().setAllowedOrigins(Collections.singletonList("de.tum.in.www1.artemis"));
        props.getCors().setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        props.getCors().setAllowedHeaders(Collections.singletonList("*"));
        props.getCors().setMaxAge(1800L);
        props.getCors().setAllowCredentials(true);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebConfigurerTestController()).addFilters(webConfigurer.corsFilter()).build();

        mockMvc.perform(options("/api/test-cors").header(HttpHeaders.ORIGIN, "de.tum.in.www1.artemis").header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "de.tum.in.www1.artemis"))
                .andExpect(header().string(HttpHeaders.VARY, "Origin")).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "1800"));

        mockMvc.perform(get("/api/test-cors").header(HttpHeaders.ORIGIN, "de.tum.in.www1.artemis")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "de.tum.in.www1.artemis"));
    }

    @Test
    void shouldCorsFilterOnOtherPath() throws Exception {
        props.getCors().setAllowedOrigins(Collections.singletonList("*"));
        props.getCors().setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        props.getCors().setAllowedHeaders(Collections.singletonList("*"));
        props.getCors().setMaxAge(1800L);
        props.getCors().setAllowCredentials(true);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebConfigurerTestController()).addFilters(webConfigurer.corsFilter()).build();

        mockMvc.perform(get("/test/test-cors").header(HttpHeaders.ORIGIN, "de.tum.in.www1.artemis")).andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldCorsFilterDeactivatedForNullAllowedOrigins() throws Exception {
        props.getCors().setAllowedOrigins(null);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebConfigurerTestController()).addFilters(webConfigurer.corsFilter()).build();

        mockMvc.perform(get("/api/test-cors").header(HttpHeaders.ORIGIN, "de.tum.in.www1.artemis")).andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldCorsFilterDeactivatedForEmptyAllowedOrigins() throws Exception {
        props.getCors().setAllowedOrigins(new ArrayList<>());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new WebConfigurerTestController()).addFilters(webConfigurer.corsFilter()).build();

        mockMvc.perform(get("/api/test-cors").header(HttpHeaders.ORIGIN, "de.tum.in.www1.artemis")).andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
