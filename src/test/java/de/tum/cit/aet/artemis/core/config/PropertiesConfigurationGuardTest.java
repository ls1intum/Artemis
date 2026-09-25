package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

class PropertiesConfigurationGuardTest {

    private PropertiesConfigurationGuard guard(boolean enabled, String operator, String admin, String university) {
        var guard = new PropertiesConfigurationGuard();
        ReflectionTestUtils.setField(guard, "telemetryEnabled", enabled);
        ReflectionTestUtils.setField(guard, "operatorName", operator);
        ReflectionTestUtils.setField(guard, "operatorAdminName", admin);
        ReflectionTestUtils.setField(guard, "universityName", university);
        return guard;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  ", "Admin", "Some Artemis Operator", "Your University", "<university>", "TODO" })
    void rejectsMissingAndPlaceholderMetadata(String invalid) {
        var guard = guard(true, invalid, invalid, invalid);
        assertThatIllegalArgumentException().isThrownBy(guard::afterPropertiesSet).withMessageContaining("info.operatorName").withMessageContaining("info.operatorAdminName")
                .withMessageContaining("info.universityName");
    }

    @Test
    void allowsValidMetadataAndDoesNotRequireNewFieldsWhenDisabled() {
        assertThatNoException().isThrownBy(guard(true, "AET", "Erika Muster", "Technical University of Munich")::afterPropertiesSet);
        assertThatNoException().isThrownBy(guard(false, "Some Artemis Operator", null, null)::afterPropertiesSet);
        assertThatIllegalArgumentException().isThrownBy(guard(false, " ", null, null)::afterPropertiesSet);
    }

    @Test
    void failsDuringContextStartupEvenWithLazyInitializationAndPersonalDataDisabled() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("core", "scheduling");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    java.util.Map.of("artemis.telemetry.enabled", "true", "artemis.telemetry.sendAdminDetails", "false", "info.operatorName", "University")));
            context.register(PropertiesConfigurationGuard.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }
}
