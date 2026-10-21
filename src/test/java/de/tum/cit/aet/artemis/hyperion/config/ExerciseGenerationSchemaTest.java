package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedFileDTO;
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
}
