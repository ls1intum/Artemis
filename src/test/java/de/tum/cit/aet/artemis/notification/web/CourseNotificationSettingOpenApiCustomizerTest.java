package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.notification.config.CourseNotificationSettingOpenApiCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

class CourseNotificationSettingOpenApiCustomizerTest {

    @ParameterizedTest
    @ValueSource(strings = { "DTO", "" })
    void shouldDescribeCompleteReadFlagsAndPartialWriteFlagsOnEitherSideOfSuffixProcessing(String suffix) {
        var read = new MapSchema();
        var write = new MapSchema();
        var unrelated = new MapSchema();
        var api = new OpenAPI().components(new Components().addSchemas("CourseNotificationSettingInfo" + suffix, new ObjectSchema().addProperty("notificationTypeChannels", read))
                .addSchemas("CourseNotificationSettingSpecificationRequest" + suffix, new ObjectSchema().addProperty("notificationTypeChannels", write))
                .addSchemas("UnrelatedDTO", new ObjectSchema().addProperty("notificationTypeChannels", unrelated)));
        new CourseNotificationSettingOpenApiCustomizer().customise(api);
        assertThat(read.getAdditionalProperties()).isInstanceOf(Schema.class);
        var readFlags = (Schema<?>) read.getAdditionalProperties();
        assertThat(readFlags.getProperties()).containsOnlyKeys("EMAIL", "PUSH", "WEBAPP");
        assertThat(readFlags.getRequired()).containsExactlyInAnyOrder("EMAIL", "PUSH", "WEBAPP");
        assertThat(readFlags.getAdditionalProperties()).isEqualTo(false);
        assertThat(readFlags.getProperties().get("EMAIL").getType()).isEqualTo("boolean");
        assertThat(write.getAdditionalProperties()).isInstanceOf(Schema.class);
        var writeFlags = (Schema<?>) write.getAdditionalProperties();
        assertThat(writeFlags.getProperties()).containsOnlyKeys("EMAIL", "PUSH", "WEBAPP");
        assertThat(writeFlags.getRequired()).isNull();
        assertThat(writeFlags.getProperties().get("EMAIL").getTypes()).containsExactlyInAnyOrder("boolean", "null");
        assertThat(writeFlags.getAdditionalProperties()).isEqualTo(false);
        assertThat(unrelated.getAdditionalProperties()).isNull();
    }

    @Test
    void shouldLeaveAbsentContractsAlone() {
        var customizer = new CourseNotificationSettingOpenApiCustomizer();
        customizer.customise(new OpenAPI());
        customizer.customise(new OpenAPI().components(new Components().addSchemas("CourseNotificationSettingInfo", new ObjectSchema())));
    }
}
