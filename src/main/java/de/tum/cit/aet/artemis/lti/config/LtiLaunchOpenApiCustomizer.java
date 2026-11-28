package de.tum.cit.aet.artemis.lti.config;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * Adds the LTI 1.3 launch login to the OpenAPI specification, so the client can call it through the generated API.
 * {@link Lti13LaunchFilter} answers the request in the security filter chain, so springdoc finds no controller method for it.
 * <p>
 * It runs before the other customizers: the schema customizer in {@code OpenAPIConfiguration} keeps only schemas whose names
 * end in {@code DTO}, strips that suffix from them and their references, and strips the {@code -resource} suffix from tags.
 */
@Conditional(LtiEnabled.class)
@Component
@Lazy
public class LtiLaunchOpenApiCustomizer implements OpenApiCustomizer, Ordered {

    private static final String RESPONSE_SCHEMA = "Lti13AuthenticationResponseDTO";

    private static final String JSON = "application/json";

    private static final String FORM = "application/x-www-form-urlencoded";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.components(new Components());
        }
        Schema<?> response = new ObjectSchema().addProperty("targetLinkUri", new StringSchema()).addProperty("ltiIdToken", new StringSchema()).addProperty("clientRegistrationId",
                new StringSchema());
        openApi.getComponents().addSchemas(RESPONSE_SCHEMA, response);

        Schema<?> form = new ObjectSchema().addProperty("state", new StringSchema()).addProperty("id_token", new StringSchema()).required(List.of("state", "id_token"));
        Operation login = new Operation().operationId("lti13AuthLogin").addTagsItem("lti-resource")
                .summary("Completes an LTI 1.3 launch with the state and id token that the platform sent")
                .requestBody(new RequestBody().required(true).content(new Content().addMediaType(FORM, new MediaType().schema(form))))
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("The launch succeeded").content(responseContent())).addApiResponse("401",
                        new ApiResponse().description("The user has to sign in before the launch continues")
                                .addHeaderObject("ltiSuccessLoginRequired", new Header().description("The login to prefill on the sign-in page").schema(new StringSchema()))
                                .content(responseContent())));
        openApi.path(CustomLti13Configurer.LTI13_LOGIN_PATH, new PathItem().post(login));
    }

    private static Content responseContent() {
        return new Content().addMediaType(JSON, new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + RESPONSE_SCHEMA)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
