package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedFileDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import io.swagger.v3.core.converter.ModelConverters;

class ExerciseGenerationSchemaTest {

    @Test
    void retainedFileSchemaCanBeResolvedWithTheProductionRuntimeDependencies() throws java.io.IOException {
        assertThat(java.util.Collections.list(getClass().getClassLoader().getResources("io/swagger/v3/oas/annotations/media/Schema.class")))
                .as("Only one Swagger annotation artifact may define Schema; classpath order must not decide schema compatibility").hasSize(1);
        var schemas = ModelConverters.getInstance().readAll(ExerciseGenerationRetainedFileDTO.class);
        var file = schemas.get("ExerciseGenerationRetainedFileDTO");
        assertThat(file).isNotNull();
        assertThat(file.getProperties()).containsKeys("repo", "path", "content");
        assertThat(file.getRequired()).containsExactlyInAnyOrder("repo", "path", "content");
    }

    @ParameterizedTest
    @EnumSource(RepositoryType.class)
    void legacyCodeGenerationSchemasMatchTheConfiguredEnumWireFormat(RepositoryType repositoryType) {
        var mapper = JsonObjectMapper.get();
        String expectedWireValue = repositoryType.name();
        var request = new CodeGenerationRequestDTO(repositoryType);
        var response = new CodeGenerationJobStartDTO("job", repositoryType);
        assertThat(mapper.valueToTree(request).path("repositoryType").asString()).isEqualTo(expectedWireValue);
        assertThat(mapper.valueToTree(response).path("repositoryType").asString()).isEqualTo(expectedWireValue);
        assertThat(mapper.readValue(mapper.writeValueAsString(request), CodeGenerationRequestDTO.class).repositoryType()).isEqualTo(repositoryType);
        var values = java.util.Arrays.stream(RepositoryType.values()).map(RepositoryType::name).toList();
        for (Class<?> dtoType : java.util.List.of(CodeGenerationRequestDTO.class, CodeGenerationJobStartDTO.class)) {
            io.swagger.v3.oas.models.media.Schema<?> schema = ModelConverters.getInstance().readAll(dtoType).get(dtoType.getSimpleName());
            assertThat(schema).isNotNull();
            assertThat(schema.getProperties().get("repositoryType").getEnum()).containsExactlyInAnyOrderElementsOf(values);
        }
    }

}
