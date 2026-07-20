package de.tum.cit.aet.artemis.hyperion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;

/**
 * Wire-format tests for exercise-generation DTOs.
 */
class ExerciseGenerationDtoTest {

    private final JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void generationRequestRequiresExplicitMode() {
        assertThat(validator.validate(new ExerciseGenerationRequestDTO(null, null, null))).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("mode");
            assertThat(violation.getMessage()).isEqualTo("must not be null");
        });
        assertThat(validator.validate(new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null))).isEmpty();
    }

    @Test
    void fileChangeClassifiesRepositoryAndSerializesItsDiscriminator() throws Exception {
        ExerciseGenerationFileChangeDTO change = ExerciseGenerationFileChangeDTO.of("solution/src/A.java", ExerciseGenerationFileChangeDTO.ACTION_DELETE, 7);

        assertThat(change.repo()).isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_SOLUTION);
        assertThat(change.turn()).isEqualTo(7);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(change));
        assertThat(json.get("type").asText()).isEqualTo("FILE_CHANGE");
        assertThat(json.get("action").asText()).isEqualTo("delete");
        assertThat(ExerciseGenerationFileChangeDTO.of("template/src/A.java", ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1).repo())
                .isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_TEMPLATE);
        assertThat(ExerciseGenerationFileChangeDTO.of("tests/test/ATest.java", ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1).repo())
                .isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_TESTS);
        assertThat(ExerciseGenerationFileChangeDTO.of("problem-statement.md", ExerciseGenerationFileChangeDTO.ACTION_EDIT, 1).repo())
                .isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_OTHER);
    }

    // ---- ExerciseGenerationEventDTO --------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void event_progressEvent_omitsNullTerminalFieldsAndSerialisesEnumAsName() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "go")));

        assertThat(json.get("type").asText()).isEqualTo("STARTED");
        assertThat(json.get("message").asText()).isEqualTo("go");
        // NON_EMPTY: the fields only a terminal DONE carries must not appear on a non-terminal event.
        assertThat(json.has("completionStatus")).isFalse();
        assertThat(json.has("verdict")).isFalse();
        assertThat(json.has("liveExerciseChanged")).isFalse();
    }

    // ---- ExerciseGenerationVerdictDTO ------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    void verdict_serializesReasonsEvenWhenEmpty_toMatchOpenApiContract() throws Exception {
        JsonNode accepted = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationVerdictDTO(true, true, true, 5, List.of())));
        assertThat(accepted.get("reasons")).isEmpty();
        assertThat(accepted.get("mechanicallyVerified").asBoolean()).isTrue();
        assertThat(accepted.get("testCount").asInt()).isEqualTo(5);

        JsonNode rejected = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationVerdictDTO(false, false, true, 2, List.of("solution failed"))));
        assertThat(rejected.get("reasons")).hasSize(1);
        assertThat(rejected.get("reasons").get(0).asText()).isEqualTo("solution failed");
    }

    @Test
    void status_serializesEmptyCollectionsRequiredByTheWireContract() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", false, null, List.of(), List.of(), true)));

        assertThat(json.get("events")).isEmpty();
        assertThat(json.get("fileChanges")).isEmpty();
        assertThat(json.get("revertAvailable").asBoolean()).isTrue();
        assertThat(json.get("ownedByCaller").asBoolean()).isTrue();
        assertThat(json.get("cancellable").asBoolean()).isFalse();
        assertThat(json.has("mode")).isFalse();
    }

    @Test
    void status_serializesFalseOwnershipRequiredByTheWireContract() throws Exception {
        JsonNode json = mapper
                .readTree(mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", true, GenerationMode.GENERATE, List.of(), List.of(), false, null, null, false)));

        assertThat(json.has("ownedByCaller")).isTrue();
        assertThat(json.get("ownedByCaller").asBoolean()).isFalse();
        assertThat(json.get("cancellable").asBoolean()).isFalse();
    }

    @Test
    void status_omitsDesignDocumentWhenAbsent_butIncludesItWhenCaptured() throws Exception {
        JsonNode withoutDesignDocument = mapper
                .readTree(mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", false, null, List.of(), List.of(), true, null, null, true, false, null)));
        assertThat(withoutDesignDocument.has("designDocument")).isFalse();

        JsonNode withDesignDocument = mapper.readTree(
                mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", false, null, List.of(), List.of(), true, null, null, true, false, "## Classes\n| Foo | role |")));
        assertThat(withDesignDocument.get("designDocument").asText()).isEqualTo("## Classes\n| Foo | role |");
    }
}
