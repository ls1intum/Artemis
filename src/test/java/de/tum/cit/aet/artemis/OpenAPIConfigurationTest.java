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
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

class OpenAPIConfigurationTest {

    @Test
    void shouldKeepTheResolvedIndexOverviewResponseContract() {
        var schemas = ModelConverters.getInstance().readAll(IndexOverviewDTO.class);
        var responseSchema = new Schema<>().$ref("#/components/schemas/IndexOverviewDTO");
        var response = new ApiResponse().content(new Content().addMediaType("application/json", new MediaType().schema(responseSchema)));
        var operation = new Operation().operationId("getIndexOverview").addTagsItem("Ingestion Coverage Resource").responses(new ApiResponses().addApiResponse("200", response));
        var openApi = new OpenAPI().components(new Components().schemas(new HashMap<>(schemas)))
                .paths(new Paths().addPathItem("/api/global-search/admin/index/overview", new PathItem().get(operation)));

        new OpenAPIConfiguration().schemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsOnlyKeys("IndexOverview", "IndexedCollectionCount");
        Schema<?> overview = openApi.getComponents().getSchemas().get("IndexOverview");
        assertThat(overview.getRequired()).containsExactlyInAnyOrder("weaviateReachable", "weaviateAddress", "irisEnabled", "irisReachable", "collections");
        assertThat(overview.getProperties().get("collections").getItems().get$ref()).isEqualTo("#/components/schemas/IndexedCollectionCount");
        Schema<?> collection = openApi.getComponents().getSchemas().get("IndexedCollectionCount");
        assertThat(collection.getRequired()).containsExactlyInAnyOrder("collection", "readable");
        assertThat(collection.getProperties()).containsKey("count");
        assertThat(collection.getProperties().get("count").getFormat()).isEqualTo("int64");
        assertThat(responseSchema.get$ref()).isEqualTo("#/components/schemas/IndexOverview");
        assertThat(openApi.getPaths().get("/api/global-search/admin/index/overview").getGet()).isSameAs(operation);
        assertThat(operation.getTags()).containsExactly("Ingestion Coverage");
    }

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
    void shouldKeepTheCourseFilterResponseContract() {
        Schema<?> courseSchema = new ObjectSchema().addProperty("id", new IntegerSchema().format("int64")).addProperty("title", new StringSchema()).addProperty("active",
                new BooleanSchema());
        var responseSchema = new ArraySchema().items(new Schema<>().$ref("#/components/schemas/CourseForSearchFilterDTO"));
        var response = new ApiResponse().content(new Content().addMediaType("application/json", new MediaType().schema(responseSchema)));
        var operation = new Operation().operationId("getCourseFilterOptions").addTagsItem("Global Search Resource").responses(new ApiResponses().addApiResponse("200", response));
        var openApi = new OpenAPI().components(new Components().addSchemas("CourseForSearchFilterDTO", courseSchema))
                .paths(new Paths().addPathItem("/api/search/course-options", new PathItem().get(operation)));

        new OpenAPIConfiguration().schemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsOnlyKeys("CourseForSearchFilter");
        assertThat(courseSchema.getRequired()).containsExactlyInAnyOrder("id", "active");
        assertThat(responseSchema.getItems().get$ref()).isEqualTo("#/components/schemas/CourseForSearchFilter");
        assertThat(openApi.getPaths().get("/api/search/course-options").getGet()).isSameAs(operation);
        assertThat(operation.getTags()).containsExactly("Global Search");
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
