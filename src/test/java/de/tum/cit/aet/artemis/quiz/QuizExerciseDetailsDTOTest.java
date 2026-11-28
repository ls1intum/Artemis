package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
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

    @Test
    void examDetailsRetainSourceGroupExamAndCourseForManagementConsumers() {
        var course = new Course();
        course.setId(1L);
        course.setTitle("Owning course");
        var exam = new Exam();
        exam.setId(2L);
        exam.setTitle("Source exam");
        exam.setCourse(course);
        var group = new ExerciseGroup();
        group.setId(3L);
        group.setExam(exam);
        var quiz = new QuizExercise();
        quiz.setId(4L);
        quiz.setExerciseGroup(group);

        for (var details : List.of(QuizExerciseDetailsDTO.of(quiz), QuizExerciseDetailsDTO.of(quiz, true), QuizExerciseDetailsDTO.of(quiz, true, true, false))) {
            var json = JsonObjectMapper.get().valueToTree(details);
            assertThat(json.path("exerciseGroup").path("id").asLong()).isEqualTo(group.getId());
            assertThat(json.path("exerciseGroup").path("exam").path("id").asLong()).isEqualTo(exam.getId());
            assertThat(json.path("exerciseGroup").path("exam").path("course").path("id").asLong()).isEqualTo(course.getId());
            assertThat(json.path("exerciseGroup").path("exam").has("studentExams")).isFalse();
            assertThat(json.path("exerciseGroup").path("exam").has("title")).isFalse();
            assertThat(json.path("course").path("id").asLong()).as("the existing flattened course remains available").isEqualTo(course.getId());
        }
        var json = JsonObjectMapper.get().valueToTree(QuizExerciseDetailsDTO.of(quiz, true, true, false));
        assertThat(json.path("isEditable").asBoolean()).isTrue();
        assertThat(json.path("quizEnded").asBoolean()).isTrue();
        assertThat(json.path("canBeEvaluated").asBoolean()).isFalse();
    }

    @Test
    void courseDetailsOmitExamContextAndKeepTheCourse() {
        var course = new Course();
        course.setId(1L);
        var quiz = new QuizExercise();
        quiz.setId(2L);
        quiz.setCourse(course);

        var details = QuizExerciseDetailsDTO.of(quiz);
        var json = JsonObjectMapper.get().valueToTree(details);

        assertThat(details.exerciseGroup()).isNull();
        assertThat(json.has("exerciseGroup")).isFalse();
        assertThat(json.path("course").path("id").asLong()).isEqualTo(course.getId());
    }
}
