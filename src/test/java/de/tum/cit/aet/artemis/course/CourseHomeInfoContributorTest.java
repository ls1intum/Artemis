package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import de.tum.cit.aet.artemis.course.config.CourseHomeInfoContributor;

class CourseHomeInfoContributorTest {

    @Test
    void disabledOrIncompleteProviderDoesNotAdvertiseAvailability() {
        for (var properties : new CourseHomeAiProperties[] { new CourseHomeAiProperties(false, "LOCAL", "http://provider.example", "api-secret", "model"),
                new CourseHomeAiProperties(true, "LOCAL", null, "api-secret", "model") }) {
            var builder = new Info.Builder();
            contributor(properties).contribute(builder);
            assertThat(builder.build().getDetails()).containsEntry("courseHomeAiAvailable", false).doesNotContainKey("courseHomeAiLocation");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "local", "CLOUD" })
    void advertisesOnlyAvailabilityAndLocationWithoutConstructingAProvider(String location) {
        var builder = new Info.Builder();
        var properties = new CourseHomeAiProperties(true, location, "http://provider.example", "api-secret", "private-model");
        contributor(properties).contribute(builder);
        var details = builder.build().getDetails();
        assertThat(details).hasSize(2).containsEntry("courseHomeAiAvailable", true).containsEntry("courseHomeAiLocation", location.toUpperCase(Locale.ROOT));
        assertThat(details.toString()).doesNotContain("provider.example", "api-secret", "private-model");
    }

    private CourseHomeInfoContributor contributor(CourseHomeAiProperties properties) {
        var environment = new MockEnvironment().withProperty("artemis.course-home.ai.enabled", Boolean.toString(properties.enabled()));
        if (properties.location() != null) {
            environment.withProperty("artemis.course-home.ai.location", properties.location());
        }
        if (properties.baseUrl() != null) {
            environment.withProperty("artemis.course-home.ai.base-url", properties.baseUrl());
        }
        if (properties.apiKey() != null) {
            environment.withProperty("artemis.course-home.ai.api-key", properties.apiKey());
        }
        if (properties.model() != null) {
            environment.withProperty("artemis.course-home.ai.model", properties.model());
        }
        return new CourseHomeInfoContributor(environment);
    }

}
