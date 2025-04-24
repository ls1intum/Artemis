package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class ParticipationFilterServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "participationfilterservice";

    @Autowired
    private ParticipationFilterService participationFilterService;

    private Map<ExerciseType, Exercise> exerciseByType;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        var course = courseUtilService.addEmptyCourse();
        var textExercise = TextExerciseFactory.generateTextExercise(null, null, null, course);
        var modelingExercise = ModelingExerciseFactory.generateModelingExercise(null, null, null, null, course);
        var quizExercise = QuizExerciseFactory.generateQuizExercise(null, null, null, course);
        var fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(null, null, null, null, course);
        var programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course);

        exerciseByType = new HashMap<>();
        exerciseByType.put(ExerciseType.TEXT, textExercise);
        exerciseByType.put(ExerciseType.MODELING, modelingExercise);
        exerciseByType.put(ExerciseType.QUIZ, quizExercise);
        exerciseByType.put(ExerciseType.FILE_UPLOAD, fileUploadExercise);
        exerciseByType.put(ExerciseType.PROGRAMMING, programmingExercise);
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldFilterStudentParticipations_emptyParticipations(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        var participations = participationFilterService.findStudentParticipationsInExercise(Set.of(), exercise);
        assertThat(participations).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldFilterStudentParticipations_nullParticipations(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        var participations = participationFilterService.findStudentParticipationsInExercise(null, exercise);
        assertThat(participations).isEmpty();
    }

    @Test
    void filterForCourseDashboard_emptySubmissions() {
        var studentParticipation = new StudentParticipation();
        studentParticipation.setSubmissions(Set.of());
        participationFilterService.filterParticipationForCourseDashboard(studentParticipation, true);
        assertThat(studentParticipation.getSubmissions()).isNull();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldThrowForMoreThanOneParticipation(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        var participations = Set.of(new StudentParticipation().exercise(exercise), new StudentParticipation().exercise(exercise));
        assertThatThrownBy(() -> participationFilterService.findStudentParticipationsInExercise(participations, exercise));
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldReturnTheCorrectParticipation(ExerciseType exerciseType) {
        var relevantExercise = exerciseByType.get(exerciseType);
        var participations = Arrays.stream(ExerciseType.values()).map(type -> new StudentParticipation().exercise(exerciseByType.get(type))).collect(Collectors.toSet());

        var relevantParticipationSet = participationFilterService.findStudentParticipationsInExercise(participations, relevantExercise);
        assertThat(relevantParticipationSet).hasSize(1);
        var relevantParticipation = relevantParticipationSet.iterator().next();
        assertThat(relevantParticipation.getExercise()).isEqualTo(relevantExercise);
    }

    @Test
    void shouldAlsoReturnPracticeParticipationForProgrammingExercise() {
        var programmingExercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        var ratedParticipation = new StudentParticipation().exercise(programmingExercise);
        var practiceParticipation = new StudentParticipation().exercise(programmingExercise);
        practiceParticipation.setPracticeMode(true);

        var relevantParticipationSet = participationFilterService.findStudentParticipationsInExercise(Set.of(ratedParticipation, practiceParticipation), programmingExercise);
        assertThat(relevantParticipationSet).containsExactlyInAnyOrder(ratedParticipation, practiceParticipation);
    }

    @Test
    void shouldNotAllowMoreThanOnePracticeParticipationForProgrammingExercise() {
        var programmingExercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        var practiceParticipations = Set.of(new StudentParticipation().exercise(programmingExercise), new StudentParticipation().exercise(programmingExercise));
        practiceParticipations.forEach(participation -> participation.setPracticeMode(true));

        assertThatThrownBy(() -> participationFilterService.findStudentParticipationsInExercise(practiceParticipations, programmingExercise),
                "There cannot be more than one practice participation per student for programming exercises");
    }
}
