package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.notification.config.SystemNotificationOpenApiCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

class SystemNotificationOpenApiCustomizerTest {

    @ParameterizedTest
    @ValueSource(strings = { "SystemNotificationUpdateDTO", "SystemNotificationUpdate" })
    void shouldCompleteOnlyTheNullableSystemRequestEnumRegardlessOfDtoSuffixOrder(String schemaName) {
        var type = new Schema<String>();
        type.setTypes(Set.of("string", "null"));
        type.setEnum(new ArrayList<>(List.of("WARNING", "INFO")));
        var otherType = new Schema<String>();
        otherType.setTypes(Set.of("string", "null"));
        otherType.setEnum(new ArrayList<>(List.of("OTHER")));
        var api = new OpenAPI().components(
                new Components().addSchemas(schemaName, new Schema<>().addProperty("type", type)).addSchemas("UnrelatedDTO", new Schema<>().addProperty("type", otherType)));
        var customizer = new SystemNotificationOpenApiCustomizer("artemisApp");
        customizer.customise(api);
        customizer.customise(api);
        assertThat(type.getEnum()).containsExactly("WARNING", "INFO", null);
        assertThat(otherType.getEnum()).containsExactly("OTHER");
    }

    @Test
    void shouldResolveConfiguredAlertNamesOnlyOnSystemAdminOperationsAndPreserveLocation() {
        var system = new ApiResponse().addHeaderObject("X-artemisApp-alert", new Header()).addHeaderObject("X-artemisApp-params", new Header()).addHeaderObject("Location",
                new Header());
        var unrelated = new ApiResponse().addHeaderObject("X-artemisApp-alert", new Header());
        var api = new OpenAPI().paths(new Paths()
                .addPathItem("/api/notification/admin/system-notifications", new PathItem().post(new Operation().responses(new ApiResponses().addApiResponse("201", system))))
                .addPathItem("/api/other", new PathItem().post(new Operation().responses(new ApiResponses().addApiResponse("201", unrelated)))));
        new SystemNotificationOpenApiCustomizer("customApp").customise(api);
        assertThat(system.getHeaders()).containsOnlyKeys("X-customApp-alert", "X-customApp-params", "Location");
        assertThat(unrelated.getHeaders()).containsOnlyKeys("X-artemisApp-alert");
    }
}
