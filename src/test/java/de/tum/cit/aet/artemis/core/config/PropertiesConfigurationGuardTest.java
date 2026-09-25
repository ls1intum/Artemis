package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

class PropertiesConfigurationGuardTest {

    private PropertiesConfigurationGuard guard(String operator, String admin, String university) {
        var guard = new PropertiesConfigurationGuard();
        ReflectionTestUtils.setField(guard, "operatorName", operator);
        ReflectionTestUtils.setField(guard, "operatorAdminName", admin);
        ReflectionTestUtils.setField(guard, "universityName", university);
        return guard;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "Admin", "Some Artemis Operator", "Your University", "<university>", "TODO", "Example University", "Max Mustermann", "Anonymous University",
            "anonymous university admin" })
    void rejectsMissingAndPlaceholderMetadata(String invalid) {
        assertThatIllegalArgumentException().isThrownBy(guard(invalid, "Erika Muster", "Technical University of Munich")::afterPropertiesSet)
                .withMessageContaining("info.operatorName (INFO_OPERATORNAME)");
        assertThatIllegalArgumentException().isThrownBy(guard("AET", invalid, "Technical University of Munich")::afterPropertiesSet)
                .withMessageContaining("info.operatorAdminName (INFO_OPERATORADMINNAME)");
        assertThatIllegalArgumentException().isThrownBy(guard("AET", "Erika Muster", invalid)::afterPropertiesSet)
                .withMessageContaining("info.universityName (INFO_UNIVERSITYNAME)");
    }

    @Test
    void allowsValidMetadata() {
        assertThatNoException().isThrownBy(guard("AET", "Erika Muster", "Technical University of Munich")::afterPropertiesSet);
    }

    @ParameterizedTest
    @ValueSource(strings = { "core,scheduling", "core", "buildagent", "dev,core", "prod,core,buildagent" })
    void rejectsInvalidMetadataOnEveryCoreNodeAndBuildAgent(String profiles) {
        try (var context = contextWithOperatorNameOnly(profiles)) {
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("info.operatorAdminName")
                    .hasStackTraceContaining("info.universityName");
        }
    }

    @Test
    void isNotRegisteredOnNodesThatAreNeitherCoreNorBuildAgent() {
        try (var context = contextWithOperatorNameOnly("prod,scheduling")) {
            context.refresh();
            assertThat(context.getBeansOfType(PropertiesConfigurationGuard.class)).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext contextWithOperatorNameOnly(String profiles) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles.split(","));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("info.operatorName", "AET")));
        context.register(PropertiesConfigurationGuard.class);
        return context;
    }
}
