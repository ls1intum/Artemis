package de.tum.cit.aet.artemis.assessment.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;

import de.tum.cit.aet.artemis.OpenAPIConfiguration;
import de.tum.cit.aet.artemis.assessment.config.ComplaintQueryOpenApiCustomizer;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

/**
 * Pins how {@link ComplaintQueryOpenApiCustomizer} describes the complaint list reads in the OpenAPI specification.
 * <p>
 * The customizer and {@code OpenAPIConfiguration#schemaCustomizer} run in springdoc's order on a specification that holds the merged
 * operations springdoc produces. Each path must end with one required query object whose shapes are exactly the query parameters of
 * the {@code ComplaintResource} handlers mapped to it, and every schema it references must survive the DTO suffix stripping.
 */
class ComplaintQueryOpenApiArchitectureTest extends AbstractArchitectureTest {

    private static final String COMPLAINTS_PATH = "/api/assessment/complaints";

    private static final String COMPLAINTS_FOR_TUTOR_PATH = "/api/assessment/complaints/for-tutor";

    private static final String SCHEMA_REFERENCE_PREFIX = "#/components/schemas/";

    private static final String COMPLAINT_RESOURCE = ARTEMIS_PACKAGE + ".assessment.web.ComplaintResource";

    private static final Map<String, String> OPERATION_IDS = Map.of(COMPLAINTS_PATH, "getComplaints", COMPLAINTS_FOR_TUTOR_PATH, "getComplaintsForTutor");

    private static OpenAPI customized;

    @BeforeAll
    static void customizeTheMergedComplaintOperations() {
        Paths paths = new Paths();
        OPERATION_IDS.keySet().forEach(path -> paths.addPathItem(path, new PathItem().get(new Operation().operationId("mergedBySpringdoc")
                .addParametersItem(new Parameter().name("courseId").in("query").required(true).schema(new IntegerSchema().format("int64"))))));
        customized = new OpenAPI().paths(paths).components(new Components().schemas(new TreeMap<>()));

        List<OpenApiCustomizer> customizers = new ArrayList<>(List.of(new OpenAPIConfiguration().schemaCustomizer(), new ComplaintQueryOpenApiCustomizer()));
        // springdoc applies the OpenApiCustomizer beans in this order, see SpringDocCustomizers#getOpenApiCustomizersStream.
        customizers.sort(AnnotationAwareOrderComparator.INSTANCE);
        customizers.forEach(customizer -> customizer.customise(customized));
    }

    @Test
    void eachComplaintListPathTakesOneRequiredQueryObject() {
        OPERATION_IDS.forEach((path, operationId) -> {
            Operation operation = customized.getPaths().get(path).getGet();
            assertThat(operation.getOperationId()).as("operation id of GET " + path).isEqualTo(operationId);
            assertThat(operation.getParameters()).as("parameters of GET " + path).singleElement().satisfies(parameter -> {
                assertThat(parameter.getName()).isEqualTo("query");
                assertThat(parameter.getIn()).isEqualTo("query");
                assertThat(parameter.getRequired()).isTrue();
                assertThat(parameter.getStyle()).isEqualTo(Parameter.StyleEnum.FORM);
                assertThat(parameter.getExplode()).isTrue();
            });
        });
    }

    @Test
    void theQueryShapesAreTheQueryParametersOfTheHandlers() {
        OPERATION_IDS.keySet().forEach(path -> {
            Schema<?> query = resolve(customized.getPaths().get(path).getGet().getParameters().getFirst().getSchema());
            Set<String> documented = new TreeSet<>();
            for (Schema<?> shapeReference : query.getOneOf()) {
                Schema<?> shape = resolve(shapeReference);
                assertThat(shape.getType()).as("type of " + shapeReference.get$ref()).isEqualTo("object");
                // Closed shapes keep the oneOf alternatives apart: a request matches only the shape whose properties it sends.
                assertThat(shape.getAdditionalProperties()).as("additional properties of " + shapeReference.get$ref()).isEqualTo(false);
                List<String> parameters = new ArrayList<>();
                shape.getProperties().forEach((name, property) -> parameters.add(describe(name, shape.getRequired().contains(name), property)));
                documented.add(String.join(", ", new TreeSet<>(parameters)));
            }
            assertThat(documented).as("query shapes of GET " + path + " against the handlers in ComplaintResource").containsExactlyInAnyOrderElementsOf(handlerQueries(path));
        });
    }

    private static Schema<?> resolve(Schema<?> reference) {
        assertThat(reference.get$ref()).as("a schema reference").startsWith(SCHEMA_REFERENCE_PREFIX);
        Schema<?> schema = customized.getComponents().getSchemas().get(reference.get$ref().substring(SCHEMA_REFERENCE_PREFIX.length()));
        assertThat(schema).as("component schema " + reference.get$ref() + " after the DTO suffix stripping").isNotNull();
        return schema;
    }

    private static String describe(String name, boolean required, Schema<?> property) {
        String type = property.getEnum() != null ? property.getType() + property.getEnum() : property.getType() + (property.getFormat() != null ? "/" + property.getFormat() : "");
        return name + (required ? "" : "?") + ": " + type;
    }

    /**
     * The query parameters of each handler of {@code ComplaintResource} that serves GET on the given path, described like the shapes.
     */
    private static Set<String> handlerQueries(String path) {
        Set<String> queries = new TreeSet<>();
        for (Method method : productionClasses.get(COMPLAINT_RESOURCE).reflect().getDeclaredMethods()) {
            GetMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
            if (mapping == null || Arrays.stream(mapping.value()).noneMatch(value -> ("/api/assessment/" + value).equals(path))) {
                continue;
            }
            List<String> parameters = new ArrayList<>();
            for (java.lang.reflect.Parameter parameter : method.getParameters()) {
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    boolean required = requestParam.required() && ValueConstants.DEFAULT_NONE.equals(requestParam.defaultValue());
                    parameters.add(parameter.getName() + (required ? "" : "?") + ": " + typeOf(parameter.getType()));
                }
            }
            queries.add(String.join(", ", new TreeSet<>(parameters)));
        }
        assertThat(queries).as("handlers of GET " + path).isNotEmpty();
        return queries;
    }

    private static String typeOf(Class<?> type) {
        if (type == Long.class) {
            return "integer/int64";
        }
        if (type == boolean.class) {
            return "boolean";
        }
        if (type == ComplaintType.class) {
            return "string" + Arrays.stream(ComplaintType.values()).map(Enum::name).toList();
        }
        throw new IllegalArgumentException("No OpenAPI type for the query parameter type " + type);
    }
}
