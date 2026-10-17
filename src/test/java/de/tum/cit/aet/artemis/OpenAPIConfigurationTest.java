package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;

class OpenAPIConfigurationTest {

    @Test
    void shouldGiveEverySearchTermSubclassItsOwnSchemaName() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(SearchTermPageableSearchDTO.class));
        for (BeanDefinition candidate : scanner.findCandidateComponents("de.tum.cit.aet.artemis")) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), getClass().getClassLoader());
            if (type != SearchTermPageableSearchDTO.class) {
                assertThat(type.getDeclaredAnnotation(io.swagger.v3.oas.annotations.media.Schema.class)).as(type.getName() + " declares its own @Schema name").isNotNull();
            }
        }
    }

    record PrimitiveComponentsDTO(long count, boolean active, Long boxedCount, String title) {
    }

    @Test
    void shouldMarkOnlyPrimitiveRecordComponentsRequired() {
        Schema<?> schema = new ObjectSchema().addProperty("count", new IntegerSchema()).addProperty("active", new BooleanSchema()).addProperty("boxedCount", new IntegerSchema())
                .addProperty("title", new StringSchema());
        schema.addRequiredItem("title");
        schema.addRequiredItem("count");
        Map<String, Schema> schemas = new HashMap<>(Map.of("PrimitiveComponentsDTO", schema));

        OpenAPIConfiguration.markPrimitiveRecordComponentsRequired(schemas);

        assertThat(schema.getRequired()).containsExactlyInAnyOrder("title", "count", "active");
    }

    @Test
    void shouldPointParameterSchemasAtTheRenamedDtoSchema() {
        Parameter filter = new QueryParameter().name("filter").schema(new Schema<>().$ref("#/components/schemas/PostFilterDTO"));
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("PostFilterDTO", new ObjectSchema().addProperty("courseId", new IntegerSchema())))
                .paths(new Paths().addPathItem("/api/posts", new PathItem().get(new Operation().operationId("getPosts").addParametersItem(filter))));

        new OpenAPIConfiguration().schemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsKey("PostFilter");
        assertThat(filter.getSchema().get$ref()).isEqualTo("#/components/schemas/PostFilter");
    }

    @Test
    void shouldKeepSpringsPageableSchema() {
        Parameter pageable = new QueryParameter().name("pageable").schema(new Schema<>().$ref("#/components/schemas/Pageable"));
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("Pageable", new ObjectSchema().addProperty("page", new IntegerSchema())))
                .paths(new Paths().addPathItem("/api/audits", new PathItem().get(new Operation().operationId("getAudits").addParametersItem(pageable))));

        new OpenAPIConfiguration().schemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsKey("Pageable");
        assertThat(pageable.getSchema().get$ref()).isEqualTo("#/components/schemas/Pageable");
    }
}
