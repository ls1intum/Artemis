package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exam.dto.ExerciseImportDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

class ExerciseImportDTOJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsExerciseTypeFromDtoProperty() throws Exception {
        ExerciseImportDTO dto = mapper.readValue("{\"id\":1,\"exerciseType\":\"programming\",\"title\":\"t\"}", ExerciseImportDTO.class);
        assertThat(dto.exerciseType()).isEqualTo(ExerciseType.PROGRAMMING);
    }

    @Test
    void readsEchoedEntityPayloadCarryingTypeAndExerciseType() throws Exception {
        ExerciseImportDTO dto = mapper.readValue("{\"id\":1,\"type\":\"programming\",\"exerciseType\":\"programming\",\"title\":\"t\"}", ExerciseImportDTO.class);
        assertThat(dto.exerciseType()).isEqualTo(ExerciseType.PROGRAMMING);
    }
}
