package de.tum.cit.aet.artemis.fileupload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadExerciseContextDTO;

class FileUploadExerciseContextDTOTest {

    @Test
    void optionalExerciseContextDoesNotInventAnExerciseOrPermissions() {
        assertThat(FileUploadExerciseContextDTO.of(null, true)).isNull();

        var course = new Course();
        course.setId(10L);
        var exercise = new FileUploadExercise();
        exercise.setId(20L);
        exercise.setCourse(course);
        exercise.setGradingCriteria(GradingCriterionDTO.toEntities(List.of(new GradingCriterionDTO(null, "Zulu", List.of()), new GradingCriterionDTO(null, "Alpha", List.of()))));

        var withoutRubric = FileUploadExerciseContextDTO.of(exercise, false);
        var withRubric = FileUploadExerciseContextDTO.of(exercise, true);

        assertThat(withoutRubric).isNotNull();
        assertThat(withoutRubric.gradingCriteria()).isNull();
        assertThat(withRubric).isNotNull();
        assertThat(withRubric.id()).isEqualTo(20L);
        assertThat(withRubric.course().id()).isEqualTo(10L);
        assertThat(withRubric.isAtLeastTutor()).isNull();
        assertThat(withRubric.isAtLeastEditor()).isNull();
        assertThat(withRubric.isAtLeastInstructor()).isNull();
        assertThat(withRubric.gradingCriteria()).extracting(GradingCriterionDTO::title).containsExactly("Zulu", "Alpha");
    }
}
