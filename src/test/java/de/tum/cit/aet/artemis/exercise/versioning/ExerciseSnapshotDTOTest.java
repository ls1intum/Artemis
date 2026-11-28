package de.tum.cit.aet.artemis.exercise.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.yaml.YAMLMapper;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseSnapshotDTOTest {

    @Test
    void describesTheExerciseVersionReadsInOpenApi() throws IOException {
        var spec = YAMLMapper.builder().build().readTree(Path.of("config/openapi/openapi.yaml").toFile());
        String listPath = "/api/exercise/exercises/{exerciseId}/versions";
        var listResponse = spec.path("paths").path(listPath).path("get").path("responses").path("200");
        var listSchema = listResponse.path("content").path("application/json").path("schema");
        assertThat(listSchema.path("type").asString()).isEqualTo("array");
        assertThat(listSchema.path("items").path("$ref").asString()).isEqualTo("#/components/schemas/ExerciseVersionMetadata");
        assertThat(listResponse.path("headers").propertyNames()).containsExactlyInAnyOrder("Link", "X-Total-Count");

        var snapshotResponse = spec.path("paths").path(listPath + "/{versionId}").path("get").path("responses").path("200");
        assertThat(snapshotResponse.path("content").path("application/json").path("schema").path("$ref").asString()).isEqualTo("#/components/schemas/ExerciseSnapshot");

        var schemas = spec.path("components").path("schemas");
        var snapshot = schemas.path("ExerciseSnapshot");
        assertThat(snapshot.path("required").values()).extracting(node -> node.asString()).containsExactly("id");
        assertThat(snapshot.has("discriminator")).isFalse();
        Map.of("programmingData", "ProgrammingExerciseSnapshot", "textData", "TextExerciseSnapshot", "modelingData", "ModelingExerciseSnapshot", "quizData", "QuizExerciseSnapshot",
                "fileUploadData", "FileUploadExerciseSnapshot").forEach((property, schema) -> {
                    assertThat(snapshot.path("properties").path(property).path("$ref").asString()).isEqualTo("#/components/schemas/" + schema);
                    assertThat(schemas.path(schema).path("type").asString()).isEqualTo("object");
                });
    }

    @Test
    void keepsAnEmptyRubricNullAndOmitsItFromSnapshotJson() {
        var exercise = new TextExercise();
        exercise.setId(42L);

        var snapshot = ExerciseSnapshotDTO.of(exercise, null, null);

        assertThat(snapshot.gradingCriteria()).isNull();
        assertThat(JsonObjectMapper.get().valueToTree(snapshot).has("gradingCriteria")).isFalse();
    }

    @Test
    void snapshotsCriteriaAndInstructionsInPersistedOrder() {
        var exercise = new TextExercise();
        exercise.setId(42L);
        var zulu = criterion(30L, "Zulu category", 0);
        var alpha = criterion(10L, "Alpha category", 1);
        var middle = criterion(20L, "Middle category", 2);
        var zuluInstruction = instruction(90L, "Zulu instruction", 0);
        var alphaInstruction = instruction(80L, "Alpha instruction", 1);
        zulu.setStructuredGradingInstructions(new HashSet<>(List.of(alphaInstruction, zuluInstruction)));
        exercise.setGradingCriteria(new HashSet<>(List.of(alpha, middle, zulu)));

        var snapshot = ExerciseSnapshotDTO.of(exercise, null, null);

        assertThat(snapshot.gradingCriteria()).extracting(GradingCriterionDTO::title).containsExactly("Zulu category", "Alpha category", "Middle category");
        assertThat(snapshot.gradingCriteria().getFirst().structuredGradingInstructions()).extracting(GradingInstructionDTO::instructionDescription)
                .containsExactly("Zulu instruction", "Alpha instruction");
        var serialized = JsonObjectMapper.get().valueToTree(snapshot);
        assertThat(serialized.get("gradingCriteria").get(0).get("title").asString()).isEqualTo("Zulu category");
        assertThat(serialized.get("gradingCriteria").get(0).get("structuredGradingInstructions").get(0).get("instructionDescription").asString()).isEqualTo("Zulu instruction");
    }

    private static GradingCriterion criterion(long id, String title, long sortOrder) {
        var criterion = new GradingCriterion();
        criterion.setId(id);
        criterion.setTitle(title);
        criterion.setSortOrder(sortOrder);
        return criterion;
    }

    private static GradingInstruction instruction(long id, String description, long sortOrder) {
        var instruction = new GradingInstruction();
        instruction.setId(id);
        instruction.setInstructionDescription(description);
        instruction.setSortOrder(sortOrder);
        return instruction;
    }
}
