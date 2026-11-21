package de.tum.cit.aet.artemis.assessment.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * Describes the complaint list reads of {@code ComplaintResource} in the OpenAPI specification as one query object per path.
 * <p>
 * Several handlers share {@code GET /api/assessment/complaints} and {@code GET /api/assessment/complaints/for-tutor} and differ only in
 * the {@code params} condition of their mapping. OpenAPI has one operation per path and method, so springdoc merges them into a single
 * operation with the union of their query parameters, all wrongly required, named after whichever handler it reads first. The generated
 * client could not send any of the real requests.
 * <p>
 * This customizer replaces the merged parameters with one parameter {@value #QUERY_PARAMETER} ({@code in: query}, {@code style: form},
 * {@code explode: true}), whose schema is a {@code oneOf} with one object per handler. Form style with explode sends each property as its
 * own query parameter, which is exactly the request the handlers match today. Each object allows only its own properties: without that,
 * {@code {exerciseId, complaintType}} would match both the test run and the exercise shape, and {@code oneOf} allows exactly one match.
 * It also pins the operation ids. The handlers, their mappings and their access checks are unchanged.
 * <p>
 * {@code OpenAPIConfiguration#schemaCustomizer} keeps only component schemas whose names end in {@value #DTO_SUFFIX} and strips that
 * suffix, but leaves parameter references untouched. The schemas are therefore registered with the suffix and referenced without it,
 * and this customizer runs first: an {@link OpenApiCustomizer} without an order runs last.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ComplaintQueryOpenApiCustomizer implements OpenApiCustomizer {

    static final String COMPLAINTS_PATH = "/api/assessment/complaints";

    static final String COMPLAINTS_FOR_TUTOR_PATH = "/api/assessment/complaints/for-tutor";

    static final String QUERY_PARAMETER = "query";

    private static final String DTO_SUFFIX = "DTO";

    private static final String SCHEMA_REFERENCE_PREFIX = "#/components/schemas/";

    /**
     * The query parameters one handler takes, and which of them it requires.
     */
    private record QueryShape(String name, Map<String, Schema<?>> properties, List<String> required) {
    }

    @Override
    public void customise(OpenAPI openApi) {
        replaceParametersWithQueryObject(openApi, COMPLAINTS_PATH, "getComplaints", "ComplaintsQuery", List.of(
                new QueryShape("ComplaintBySubmissionQuery", Map.of("submissionId", id()), List.of("submissionId")),
                new QueryShape("TestRunComplaintsQuery", Map.of("exerciseId", id()), List.of("exerciseId")),
                new QueryShape("CourseComplaintsQuery", Map.of("courseId", id(), "complaintType", complaintType(), "tutorId", id(), "allComplaintsForTutor", new BooleanSchema()),
                        List.of("courseId", "complaintType")),
                new QueryShape("ExerciseComplaintsQuery",
                        Map.of("exerciseId", id(), "complaintType", complaintType(), "tutorId", id(), "allComplaintsForTutor", new BooleanSchema()),
                        List.of("exerciseId", "complaintType")),
                new QueryShape("ExamComplaintsQuery", Map.of("courseId", id(), "examId", id()), List.of("courseId", "examId"))));
        replaceParametersWithQueryObject(openApi, COMPLAINTS_FOR_TUTOR_PATH, "getComplaintsForTutor", "ComplaintsForTutorQuery", List.of(
                new QueryShape("CourseComplaintsForTutorQuery", Map.of("courseId", id(), "complaintType", complaintType(), "allComplaintsForTutor", new BooleanSchema()),
                        List.of("courseId", "complaintType")),
                new QueryShape("ExerciseComplaintsForTutorQuery", Map.of("exerciseId", id(), "complaintType", complaintType(), "allComplaintsForTutor", new BooleanSchema()),
                        List.of("exerciseId", "complaintType"))));
    }

    private static void replaceParametersWithQueryObject(OpenAPI openApi, String path, String operationId, String queryName, List<QueryShape> shapes) {
        PathItem pathItem = openApi.getPaths() == null ? null : openApi.getPaths().get(path);
        if (pathItem == null || pathItem.getGet() == null) {
            return;
        }
        ComposedSchema query = new ComposedSchema();
        for (QueryShape shape : shapes) {
            ObjectSchema schema = new ObjectSchema();
            new TreeMap<>(shape.properties()).forEach(schema::addProperty);
            schema.setRequired(shape.required());
            schema.setAdditionalProperties(false);
            openApi.getComponents().addSchemas(shape.name() + DTO_SUFFIX, schema);
            query.addOneOfItem(reference(shape.name()));
        }
        openApi.getComponents().addSchemas(queryName + DTO_SUFFIX, query);

        Operation operation = pathItem.getGet();
        operation.setOperationId(operationId);
        operation.setParameters(
                List.of(new Parameter().name(QUERY_PARAMETER).in("query").required(true).style(Parameter.StyleEnum.FORM).explode(true).schema(reference(queryName))));
    }

    private static Schema<?> reference(String schemaName) {
        return new Schema<>().$ref(SCHEMA_REFERENCE_PREFIX + schemaName);
    }

    private static Schema<?> id() {
        return new IntegerSchema().format("int64");
    }

    private static Schema<?> complaintType() {
        return new StringSchema()._enum(Arrays.stream(ComplaintType.values()).map(Enum::name).toList());
    }
}
