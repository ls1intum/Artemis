package de.tum.cit.aet.artemis.core.config.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

class NullableSuccessBodyCustomizerTest {

    private static final String SUBMISSION_REFERENCE = "#/components/schemas/FileUploadSubmissionDTO";

    private final NullableSuccessBodyCustomizer customizer = new NullableSuccessBodyCustomizer();

    @Test
    void wrapsTheSuccessSchemaOfAnAnnotatedHandlerInAUnionWithNull() throws NoSuchMethodException {
        Operation operation = operationWithSuccessReference();

        customizer.customize(operation, handlerMethod("annotated"));

        Schema<?> schema = jsonSchema(operation, "200");
        assertThat(schema.get$ref()).isNull();
        assertThat(schema.getOneOf()).hasSize(2);
        assertThat(schema.getOneOf().getFirst().get$ref()).isEqualTo(SUBMISSION_REFERENCE);
        assertThat(schema.getOneOf().get(1).getTypes()).isEqualTo(Set.of("null"));
    }

    @Test
    void leavesAHandlerWithoutTheAnnotationUnchanged() throws NoSuchMethodException {
        Operation operation = operationWithSuccessReference();

        customizer.customize(operation, handlerMethod("notAnnotated"));

        Schema<?> schema = jsonSchema(operation, "200");
        assertThat(schema.get$ref()).isEqualTo(SUBMISSION_REFERENCE);
        assertThat(schema.getOneOf()).isNull();
    }

    @Test
    void leavesOtherResponsesUnchanged() throws NoSuchMethodException {
        Operation operation = operationWithSuccessReference();
        operation.getResponses().addApiResponse("201", jsonResponse(new Schema<>().$ref(SUBMISSION_REFERENCE)));

        customizer.customize(operation, handlerMethod("annotated"));

        assertThat(jsonSchema(operation, "201").get$ref()).isEqualTo(SUBMISSION_REFERENCE);
    }

    @Test
    void rejectsAnAnnotatedHandlerWithoutAJsonSuccessSchema() throws NoSuchMethodException {
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
        HandlerMethod handlerMethod = handlerMethod("annotated");

        assertThatIllegalStateException().isThrownBy(() -> customizer.customize(operation, handlerMethod)).withMessageContaining("annotated");
    }

    private static Operation operationWithSuccessReference() {
        return new Operation().responses(new ApiResponses().addApiResponse("200", jsonResponse(new Schema<>().$ref(SUBMISSION_REFERENCE))));
    }

    private static ApiResponse jsonResponse(Schema<?> schema) {
        return new ApiResponse().description("OK").content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private static Schema<?> jsonSchema(Operation operation, String statusCode) {
        return operation.getResponses().get(statusCode).getContent().get("application/json").getSchema();
    }

    private static HandlerMethod handlerMethod(String name) throws NoSuchMethodException {
        return new HandlerMethod(new Handlers(), Handlers.class.getDeclaredMethod(name));
    }

    private static class Handlers {

        @NullableSuccessBody
        void annotated() {
        }

        void notAnnotated() {
        }
    }
}
