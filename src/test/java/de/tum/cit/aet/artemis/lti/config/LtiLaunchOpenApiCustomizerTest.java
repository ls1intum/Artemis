package de.tum.cit.aet.artemis.lti.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Pins how {@link LtiLaunchOpenApiCustomizer} describes the LTI 1.3 launch login, which the security filter chain answers and springdoc
 * therefore does not find.
 */
class LtiLaunchOpenApiCustomizerTest {

    private static final String RESPONSE_REFERENCE = "#/components/schemas/Lti13AuthenticationResponseDTO";

    private final LtiLaunchOpenApiCustomizer customizer = new LtiLaunchOpenApiCustomizer();

    @Test
    void describesTheLaunchLoginAsAFormPost() {
        OpenAPI openApi = new OpenAPI();

        customizer.customise(openApi);

        Operation login = openApi.getPaths().get(CustomLti13Configurer.LTI13_LOGIN_PATH).getPost();
        assertThat(login.getOperationId()).isEqualTo("lti13AuthLogin");
        assertThat(login.getTags()).containsExactly("lti-resource");
        assertThat(login.getRequestBody().getRequired()).isTrue();
        Schema<?> form = login.getRequestBody().getContent().get("application/x-www-form-urlencoded").getSchema();
        assertThat(form.getProperties()).containsOnlyKeys("state", "id_token");
        assertThat(form.getRequired()).containsExactlyInAnyOrder("state", "id_token");
    }

    @Test
    void answersSuccessAndSignInRequiredWithTheLaunchResponse() {
        OpenAPI openApi = new OpenAPI();

        customizer.customise(openApi);

        Operation login = openApi.getPaths().get(CustomLti13Configurer.LTI13_LOGIN_PATH).getPost();
        assertThat(login.getResponses()).containsOnlyKeys("200", "401");
        for (ApiResponse response : List.of(login.getResponses().get("200"), login.getResponses().get("401"))) {
            assertThat(response.getContent().get("application/json").getSchema().get$ref()).isEqualTo(RESPONSE_REFERENCE);
        }
        assertThat(login.getResponses().get("401").getHeaders()).containsOnlyKeys("ltiSuccessLoginRequired");
        assertThat(openApi.getComponents().getSchemas().get("Lti13AuthenticationResponseDTO").getProperties()).containsOnlyKeys("targetLinkUri", "ltiIdToken",
                "clientRegistrationId");
    }

    @Test
    void keepsTheExistingComponentSchemas() {
        Schema<?> existing = new Schema<>();
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("OtherDTO", existing));

        customizer.customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsEntry("OtherDTO", existing).containsKey("Lti13AuthenticationResponseDTO");
    }

    @Test
    void runsBeforeTheSchemaCustomizer() {
        assertThat(customizer.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
