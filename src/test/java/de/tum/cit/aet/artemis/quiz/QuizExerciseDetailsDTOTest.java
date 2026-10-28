package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseDetailsDTO;

class QuizExerciseDetailsDTOTest {

    @Test
    void editableDetailsPreservePrerequisiteTypeAndOrderedRubricOnTheWire() {
        var exercise = new QuizExercise();
        exercise.setId(20L);
        var prerequisite = new Prerequisite();
        prerequisite.setId(30L);
        prerequisite.setTitle("Required knowledge");
        exercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(prerequisite, exercise, 0.5)));
        exercise.setGradingCriteria(GradingCriterionDTO.toEntities(List.of(new GradingCriterionDTO(null, "Zulu", List.of()), new GradingCriterionDTO(null, "Alpha", List.of()))));

        var details = QuizExerciseDetailsDTO.of(exercise, false);
        var json = JsonObjectMapper.get().valueToTree(details);

        assertThat(details.isEditable()).isFalse();
        assertThat(details.canBeEvaluated()).isNull();
        assertThat(details.gradingCriteria()).extracting(GradingCriterionDTO::title).containsExactly("Zulu", "Alpha");
        var linkedPrerequisite = json.path("competencyLinks").get(0);
        assertThat(linkedPrerequisite.path("weight").asDouble()).isEqualTo(0.5);
        assertThat(linkedPrerequisite.path("competency").path("type").asString()).isEqualTo("prerequisite");
        assertThat(linkedPrerequisite.path("competency").path("id").asLong()).isEqualTo(30L);
        assertThat(json.path("gradingCriteria").get(0).path("title").asString()).isEqualTo("Zulu");
    }
}
