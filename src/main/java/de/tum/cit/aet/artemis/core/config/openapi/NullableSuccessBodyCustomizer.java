package de.tum.cit.aet.artemis.core.config.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Documents the {@code 200} response of a handler annotated with {@link NullableSuccessBody} as nullable: every JSON schema of that response becomes
 * {@code oneOf: [<schema>, {type: 'null'}]}. OpenAPI 3.1 has no {@code nullable} keyword, and a {@code $ref} admits no sibling {@code type}, so this union is
 * the form a generated client reads as {@code T | null}.
 * <p>
 * The schema stays a reference, so the shared component itself remains non-nullable for every other operation that uses it.
 */
public class NullableSuccessBodyCustomizer implements OperationCustomizer {

    private static final String OK_STATUS_CODE = "200";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(NullableSuccessBody.class)) {
            makeSuccessBodyNullable(operation, handlerMethod.getMethod().toGenericString());
        }
        return operation;
    }

    /**
     * Wraps every JSON schema of the {@code 200} response of the given operation in a union with {@code null}.
     *
     * @param operation the operation to change in place
     * @param handler   the handler the operation documents, named in the error
     * @throws IllegalStateException if the operation documents no {@code 200} response with a JSON schema, so the annotation would have no effect
     */
    static void makeSuccessBodyNullable(Operation operation, String handler) {
        ApiResponse okResponse = operation.getResponses() == null ? null : operation.getResponses().get(OK_STATUS_CODE);
        Content content = okResponse == null ? null : okResponse.getContent();
        List<MediaType> jsonMediaTypes = content == null ? List.of()
                : content.entrySet().stream().filter(entry -> entry.getKey().contains("json") && entry.getValue().getSchema() != null).map(Map.Entry::getValue).toList();
        if (jsonMediaTypes.isEmpty()) {
            throw new IllegalStateException("@NullableSuccessBody on " + handler + " needs a documented 200 response with a JSON schema");
        }
        for (MediaType mediaType : jsonMediaTypes) {
            JsonSchema nullSchema = new JsonSchema();
            nullSchema.setTypes(Set.of("null"));
            JsonSchema union = new JsonSchema();
            union.setOneOf(new ArrayList<>(List.of(mediaType.getSchema(), nullSchema)));
            mediaType.setSchema(union);
        }
    }
}
