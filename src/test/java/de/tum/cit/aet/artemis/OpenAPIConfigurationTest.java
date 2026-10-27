package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedAssessmentInstructionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.CreateOrUpdateTutorialGroupRequestDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodSessionCountDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionCountDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
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

    @Test
    void shouldRequireTutorialGroupCountResponseFields() {
        var converters = ModelConverters.getInstance();
        var dailyCount = converters.readAll(TutorialGroupSessionCountDTO.class).get("TutorialGroupSessionCountDTO");
        var freePeriodCount = converters.readAll(TutorialGroupFreePeriodSessionCountDTO.class).get("TutorialGroupFreePeriodSessionCountDTO");

        assertThat(dailyCount.getRequired()).containsExactlyInAnyOrder("date", "count");
        assertThat(freePeriodCount.getRequired()).containsExactlyInAnyOrder("freePeriodId", "count");
    }

    @Test
    void shouldRequireTutorIdInTutorialGroupRequests() {
        var request = ModelConverters.getInstance().readAll(CreateOrUpdateTutorialGroupRequestDTO.class).get("CreateOrUpdateTutorialGroupRequestDTO");

        assertThat(request.getRequired()).contains("tutorId");
    }

    @Test
    void shouldRequireGeneratedAssessmentInstructionCredits() {
        var instruction = ModelConverters.getInstance().readAll(GeneratedAssessmentInstructionDTO.class).get("GeneratedAssessmentInstructionDTO");

        assertThat(instruction.getRequired()).contains("credits");
    }

    @Test
    void shouldNotMakePrimitiveRequestPropertiesRequired() {
        var request = ModelConverters.getInstance().readAll(CodeGenerationRequestDTO.class).get("CodeGenerationRequestDTO");
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("CodeGenerationRequestDTO", request));

        new OpenAPIConfiguration().schemaCustomizer().customise(openApi);

        var required = openApi.getComponents().getSchemas().get("CodeGenerationRequest").getRequired();
        assertThat(required == null || !required.contains("initialAutoGeneration")).isTrue();
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
