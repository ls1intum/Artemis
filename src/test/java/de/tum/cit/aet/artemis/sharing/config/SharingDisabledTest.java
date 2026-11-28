package de.tum.cit.aet.artemis.sharing.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.sharing.service.ExerciseSharingService;
import de.tum.cit.aet.artemis.sharing.service.ProgrammingExerciseImportFromSharingService;
import de.tum.cit.aet.artemis.sharing.service.SharingConnectorService;
import de.tum.cit.aet.artemis.sharing.service.SharingHealthIndicator;

class SharingDisabledTest {

    @Test
    void disabledSharingRegistersNeitherPlatformBeansNorRoutes() {
        new WebApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles(PROFILE_CORE)).withPropertyValues("artemis.sharing.enabled=false")
                .withUserConfiguration(SharingWebContext.class).run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(SharingConnectorService.class).doesNotHaveBean(SharingHealthIndicator.class)
                            .doesNotHaveBean(ExerciseSharingService.class).doesNotHaveBean(ProgrammingExerciseImportFromSharingService.class).doesNotHaveBean("sharingRestClient");
                    var mappings = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet();
                    assertThat(mappings.stream().flatMap(mapping -> mapping.getPatternValues().stream()))
                            .noneMatch(path -> path.startsWith("/api/core/sharing/") || path.startsWith("/api/programming/sharing/"));
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan("de.tum.cit.aet.artemis.sharing")
    @EnableWebMvc
    static class SharingWebContext {
    }
}
