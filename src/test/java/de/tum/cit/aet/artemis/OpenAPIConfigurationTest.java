package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import tools.jackson.dataformat.yaml.YAMLMapper;

import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.CourseBrowserDataDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexOverviewDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityRecordDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionCoverageDTO;
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
    void shouldKeepTheResolvedCoverageResponseGraph() {
        var schemas = ModelConverters.getInstance().readAll(IngestionCoverageDTO.class);
        var items = new Schema<>().$ref("#/components/schemas/IngestionCoverageDTO");
        var response = new ApiResponse().content(new Content().addMediaType("application/json", new MediaType().schema(new ArraySchema().items(items))));
        var operation = new Operation().operationId("getStoredCoverage").responses(new ApiResponses().addApiResponse("200", response));
        var openApi = new OpenAPI().components(new Components().schemas(new HashMap<>(schemas)))
                .paths(new Paths().addPathItem("/api/global-search/admin/coverage", new PathItem().get(operation)));

        new OpenAPIConfiguration().schemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsOnlyKeys("IngestionCoverage", "IngestionTypeCount");
        Schema<?> coverage = openApi.getComponents().getSchemas().get("IngestionCoverage");
        assertThat(coverage.getRequired()).containsExactlyInAnyOrder("courseId", "courseTitle", "releaseDate", "active", "semester", "coverageGapScore", "status", "computedAt",
                "lastIngestedAt", "typeCounts");
        assertThat(coverage.getProperties()).containsKeys("courseTitle", "releaseDate", "semester", "lastIngestedAt", "typeCounts");
        assertThat(coverage.getProperties().get("typeCounts").getItems().get$ref()).isEqualTo("#/components/schemas/IngestionTypeCount");
        Schema<?> count = openApi.getComponents().getSchemas().get("IngestionTypeCount");
        assertThat(count.getRequired()).containsExactlyInAnyOrder("type", "expected", "indexed", "missing", "orphaned");
        assertThat(items.get$ref()).isEqualTo("#/components/schemas/IngestionCoverage");
    }

    @Test
    void exportedCoverageMappingsKeepSuccessHeadersArrayContentAndPageable() {
        var specification = YAMLMapper.builder().build().readTree(Path.of("config/openapi/openapi.yaml").toFile());
        for (String path : List.of("/api/global-search/admin/coverage", "/api/global-search/admin/coverage/page")) {
            var operation = specification.get("paths").get(path).get("get");
            var response = operation.get("responses").get("200");
            var total = response.get("headers").get("X-Total-Count").get("schema");
            assertThat(total.get("type").stringValue()).isEqualTo("integer");
            assertThat(total.get("format").stringValue()).isEqualTo("int64");
            var body = response.get("content").get("application/json").get("schema");
            assertThat(body.get("type").stringValue()).isEqualTo("array");
            assertThat(body.get("items").get("$ref").stringValue()).isEqualTo("#/components/schemas/IngestionCoverage");
            assertThat(operation.get("parameters")).anySatisfy(parameter -> {
                assertThat(parameter.get("name").stringValue()).isEqualTo("pageable");
                assertThat(parameter.get("schema").get("$ref").stringValue()).isEqualTo("#/components/schemas/Pageable");
            });
        }
        var coverage = specification.get("components").get("schemas").get("IngestionCoverage");
        for (String property : List.of("courseTitle", "releaseDate", "semester", "lastIngestedAt")) {
            assertThat(coverage.get("properties").get(property).get("type")).anySatisfy(type -> assertThat(type.stringValue()).isEqualTo("null"));
        }
    }

    @Test
    void keepsTheResolvedSparseBrowserGraphAndControlledIdentityFields() {
        var schemas = new HashMap<String, Schema>();
        for (Class<?> dto : List.of(CourseBrowserDataDTO.class, IndexedEntityRecordDTO.class, IndexedContentObjectDTO.class)) {
            schemas.putAll(ModelConverters.getInstance().readAll(dto));
        }
        var openApi = new OpenAPI().components(new Components().schemas(schemas)).paths(new Paths());
        new OpenAPIConfiguration().schemaCustomizer().customise(openApi);
        var models = openApi.getComponents().getSchemas();
        assertThat(models).containsKeys("CourseBrowserData", "IndexedEntity", "IndexedContentPresence", "MissingEntity", "MissingContent", "IngestionTypeCount",
                "IndexedEntityRecord", "IndexedContentObject");
        assertThat(models.get("CourseBrowserData").getRequired()).containsExactlyInAnyOrder("entities", "contentPresence", "missingEntities", "contentGaps", "typeCounts");
        Schema<?> browserSchema = models.get("CourseBrowserData");
        assertThat(browserSchema.getProperties().get("entities").getItems().get$ref()).isEqualTo("#/components/schemas/IndexedEntity");
        assertThat(models.get("IndexedEntity").getRequired()).containsExactlyInAnyOrder("entityId", "expected");
        assertThat(models.get("IndexedContentPresence").getRequired()).containsExactly("key");
        assertThat(models.get("MissingEntity").getRequired()).containsExactlyInAnyOrder("type", "entityId");
        assertThat(models.get("IndexedEntityRecord").getRequired()).containsExactlyInAnyOrder("type", "entityId");
        Schema<?> recordSchema = models.get("IndexedEntityRecord");
        assertThat(recordSchema.getProperties().get("properties").getAdditionalProperties()).isNotNull();
        assertThat(models.get("IndexedContentObject").getRequired()).isNullOrEmpty();
        assertThat(models.get("MissingContent").getRequired()).containsExactlyInAnyOrder("lectureUnitId", "kind");
        Schema<?> missingContentSchema = models.get("MissingContent");
        Schema<?> kindSchema = missingContentSchema.getProperties().get("kind");
        assertThat(kindSchema.getEnum()).extracting(Object::toString).containsExactly("slides", "transcript");
    }

    @Test
    void exportedBrowserOperationsKeepBodyResponsesRequiredQueriesAndNullableMetadata() {
        var specification = YAMLMapper.builder().build().readTree(Path.of("config/openapi/openapi.yaml").toFile());
        String base = "/api/global-search/admin/courses/{courseId}";
        for (String path : List.of(base + "/browser", base + "/entities", base + "/units/{unitId}/content")) {
            var operation = specification.get("paths").get(path).get("get");
            var response = operation.get("responses").get("200");
            assertThat(response.has("headers")).isFalse();
            var body = response.get("content").get("application/json").get("schema");
            if (path.endsWith("/browser")) {
                assertThat(body.get("$ref").stringValue()).isEqualTo("#/components/schemas/CourseBrowserData");
            }
            else {
                assertThat(body.get("type").stringValue()).isEqualTo("array");
                String query = path.endsWith("/entities") ? "type" : "key";
                assertThat(operation.get("parameters")).anySatisfy(parameter -> {
                    assertThat(parameter.get("name").stringValue()).isEqualTo(query);
                    assertThat(parameter.get("required").booleanValue()).isTrue();
                });
            }
        }
        var models = specification.get("components").get("schemas");
        for (String model : List.of("IndexedEntity", "IndexedEntityRecord", "MissingEntity", "MissingContent")) {
            assertThat(models.get(model).get("properties").get("title").get("type")).anySatisfy(type -> assertThat(type.stringValue()).isEqualTo("null"));
        }
    }

    @Test
    void exportedRefreshOperationHasNoInputOrContentForSuccessAndFailure() {
        var specification = YAMLMapper.builder().build().readTree(Path.of("config/openapi/openapi.yaml").toFile());
        var operation = specification.get("paths").get("/api/global-search/admin/coverage/refresh").get("post");
        assertThat(operation.get("operationId").stringValue()).isEqualTo("refreshCoverage");
        assertThat(operation.has("requestBody")).isFalse();
        assertThat(operation.has("parameters")).isFalse();
        for (String status : List.of("200", "503")) {
            var response = operation.get("responses").get(status);
            assertThat(response).as("bodyless %s response", status).isNotNull();
            assertThat(response.has("content")).isFalse();
            assertThat(response.has("headers")).isFalse();
        }
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
