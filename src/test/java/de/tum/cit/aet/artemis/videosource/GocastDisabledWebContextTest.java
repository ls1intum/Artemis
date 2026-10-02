package de.tum.cit.aet.artemis.videosource;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

class GocastDisabledWebContextTest {

    private static final String CALLBACK_PATH = "/api/videosource/public/gocast/approval/callback";

    @Test
    void nativeWebContextServesDisabledFallbacksWithoutBindingService() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().setActiveProfiles(PROFILE_CORE);
            context.register(DisabledGocastWebConfiguration.class);
            context.refresh();

            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

            mockMvc.perform(get("/api/videosource/courses/41/binding")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                    .andExpect(jsonPath("$.status").value("UNLINKED"));
            mockMvc.perform(post("/api/videosource/courses/41/binding/approval")).andExpect(status().isServiceUnavailable());
            mockMvc.perform(delete("/api/videosource/courses/41/binding")).andExpect(status().isServiceUnavailable());
            mockMvc.perform(get(CALLBACK_PATH).param("state", "state").param("code", "code")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer")).andExpect(content().string(containsString("Connection not completed")));
        }
    }

    @Lazy
    @Profile(PROFILE_CORE)
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(basePackages = "de.tum.cit.aet.artemis.videosource.web", useDefaultFilters = false, includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "de\\.tum\\.cit\\.aet\\.artemis\\.videosource\\.web\\.(open\\.)?Gocast.*Resource"))
    static class DisabledGocastWebConfiguration {

        @Bean
        AuthorizationCheckService authorizationCheckService() {
            return mock(AuthorizationCheckService.class);
        }

        @Bean
        StaticMessageSource messageSource() {
            StaticMessageSource messageSource = new StaticMessageSource();
            messageSource.addMessage("gocast.callback.title", Locale.ENGLISH, "TUM.Live course connection");
            messageSource.addMessage("gocast.callback.retry.heading", Locale.ENGLISH, "Connection not completed");
            messageSource.addMessage("gocast.callback.retry.description", Locale.ENGLISH, "Return to Artemis and try again.");
            return messageSource;
        }
    }
}
