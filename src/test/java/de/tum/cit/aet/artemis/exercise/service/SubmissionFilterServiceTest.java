package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class SubmissionFilterServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "filtersubmissionservice";

    @Autowired
    private SubmissionFilterService submissionFilterService;

    private Map<ExerciseType, Exercise> exerciseByType;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
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
    @ValueSource(booleans = { true, false })
    void shouldNotFindSubmissionWhenEmpty(boolean ignoreAssessmentDueDate) {
        var submissions = submissionFilterService.getLatestSubmissionWithResult(Set.of(), ignoreAssessmentDueDate);
        assertThat(submissions).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void shouldNotFindSubmissionWhenNull(boolean ignoreAssessmentDueDate) {
        var submissions = submissionFilterService.getLatestSubmissionWithResult(null, ignoreAssessmentDueDate);
        assertThat(submissions).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldNotFindSubmissionsWithoutResult(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        var participation = new StudentParticipation().exercise(exercise);
        var submission = getSubmissionBasedOnExerciseType(exerciseType);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);

        var optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(Set.of(submission), true);

        // programming exercises follow different rules
        if (!ExerciseType.PROGRAMMING.equals(exerciseType)) {
            assertThat(optionalSubmission).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldNotFindSubmissionsWithUnratedResult(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        var participation = new StudentParticipation().exercise(exercise);
        var submission = getSubmissionBasedOnExerciseType(exerciseType);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        submission.setResults(List.of(new Result().rated(false)));

        var optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(Set.of(submission), true);

        assertThat(optionalSubmission).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldNotFindSubmissionWhenAssessmentDueDateIsNotOver(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(1));
        var participation = new StudentParticipation().exercise(exercise);
        var submission = getSubmissionBasedOnExerciseType(exerciseType);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        submission.setResults(List.of(new Result().rated(true)));

        var optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(Set.of(submission), false);

        // programming exercises follow different rules
        if (!ExerciseType.PROGRAMMING.equals(exerciseType)) {
            assertThat(optionalSubmission).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldFindSubmission(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        var participation = new StudentParticipation().exercise(exercise);
        var submission = getSubmissionBasedOnExerciseType(exerciseType);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        submission.setResults(List.of(new Result().rated(true).completionDate(ZonedDateTime.now())));

        var optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(Set.of(submission), false);

        assertThat(optionalSubmission).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldFindSubmissionWhenAssessmentDueDateIsIgnored(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(1));
        var participation = new StudentParticipation().exercise(exercise);
        var submission = getSubmissionBasedOnExerciseType(exerciseType);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        submission.setResults(List.of(new Result().rated(true)));

        var optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(Set.of(submission), true);

        // quiz exercises have no assessment due date
        if (!ExerciseType.QUIZ.equals(exerciseType)) {
            assertThat(optionalSubmission).isPresent().get().isEqualTo(submission);
        }
    }

    @ParameterizedTest
    @EnumSource(ExerciseType.class)
    void shouldGetTheLatestSubmission(ExerciseType exerciseType) {
        var exercise = exerciseByType.get(exerciseType);
        exercise.setDueDate(ZonedDateTime.now());
        var participation = new StudentParticipation().exercise(exercise);
        Set<Submission> submissions = new java.util.HashSet<>();
        Submission expectedLatestSubmission = null;

        for (int i = 0; i < 3; i++) {
            var submission = getSubmissionBasedOnExerciseType(exerciseType);
            // i + 1 one to make sure that the submission date is before the due date
            submission.setSubmissionDate(ZonedDateTime.now().minusDays(i + 1));
            // ids in the order of submission date
            submission.setId(2L - i);
            submission.setParticipation(participation);
            submission.setResults(List.of(new Result().rated(true).completionDate(ZonedDateTime.now().minusDays(i + 1))));
            submissions.add(submission);
            if (i == 0) {
                expectedLatestSubmission = submission;
            }
        }

        var latestSubmission = submissionFilterService.getLatestSubmissionWithResult(submissions, false);
        assertThat(latestSubmission).isPresent().get().isEqualTo(expectedLatestSubmission);
    }

    /// PROGRAMMING SUBMISSIONS ///

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void shouldFindAppropriateProgrammingSubmissionRespectingIndividualDueDate(boolean isSubmissionAfterIndividualDueDate) {
        var exercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        exercise.setDueDate(ZonedDateTime.now());
        var submission = new ProgrammingSubmission();
        submission.setType(SubmissionType.OTHER);
        if (isSubmissionAfterIndividualDueDate) {
            submission.setSubmissionDate(ZonedDateTime.now().plusHours(26));
        }
        else {
            // submission time after exercise due date but before individual due date
            submission.setSubmissionDate(ZonedDateTime.now().plusHours(1));
        }
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setSubmissions(Set.of(submission));
        exercise.setStudentParticipations(Set.of(participation));
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        participation.setExercise(exercise);
        submission.setParticipation(participation);
        Optional<Submission> latestValidSubmission = submissionFilterService.getLatestSubmissionWithResult(participation.getSubmissions(), false);
        if (isSubmissionAfterIndividualDueDate) {
            assertThat(latestValidSubmission).isEmpty();
        }
        else {
            assertThat(latestValidSubmission).isPresent().get().isEqualTo(submission);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void shouldOnlyReturnRatedProgrammingSubmissions(boolean isRated) {
        var exercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        var result = new Result().rated(isRated);
        var submission = new ProgrammingSubmission().submissionDate(ZonedDateTime.now().plusDays(1));
        submission.setResults(List.of(result));
        var participation = new StudentParticipation().exercise(exercise);
        participation.setSubmissions(Set.of(submission));
        submission.setParticipation(participation);
        Optional<Submission> latestValidSubmission = submissionFilterService.getLatestSubmissionWithResult(participation.getSubmissions(), false);
        if (isRated) {
            assertThat(latestValidSubmission).isPresent().get().isEqualTo(submission);
        }
        else {
            assertThat(latestValidSubmission).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(AssessmentType.class)
    void shouldReturnSubmissionWithCompletedResult_AssessmentDueDatePassed(AssessmentType assessmentType) {
        var exercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        exercise.setAssessmentDueDate(ZonedDateTime.now().minusHours(1));
        var result = new Result().rated(true).assessmentType(assessmentType).completionDate(ZonedDateTime.now().minusHours(1));
        var submission = new ProgrammingSubmission().submissionDate(ZonedDateTime.now().plusDays(1));
        submission.setResults(List.of(result));
        var participation = new StudentParticipation().exercise(exercise);
        participation.setSubmissions(Set.of(submission));
        submission.setParticipation(participation);
        Optional<Submission> latestValidSubmission = submissionFilterService.getLatestSubmissionWithResult(participation.getSubmissions(), false);
        assertThat(latestValidSubmission).isPresent().get().isEqualTo(submission);
    }

    @Test
    void shouldFindLatestAutomaticProgrammingSubmission_beforeAssessmentDueDate() {
        var programmingExercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusHours(3));

        var submissionWithoutManualAssessment = new ProgrammingSubmission();
        submissionWithoutManualAssessment.setId(1L);
        var submissionWithManualAssessment = new ProgrammingSubmission();
        submissionWithManualAssessment.setId(2L);
        var firstResult = new Result().rated(true).completionDate(ZonedDateTime.now()).assessmentType(AssessmentType.AUTOMATIC);
        firstResult.setId(1L);
        var secondResult = new Result().rated(true).score(1.0).completionDate(ZonedDateTime.now().plusHours(1)).assessmentType(AssessmentType.AUTOMATIC);
        secondResult.setId(2L);
        var retriggeredSecondResult = new Result().rated(true).score(1.0).completionDate(ZonedDateTime.now().plusHours(1).plusMinutes(30)).assessmentType(AssessmentType.AUTOMATIC);
        retriggeredSecondResult.setId(3L);
        var secondResultWithManualAssessment = new Result().score(2.0).rated(true).completionDate(ZonedDateTime.now().plusHours(2)).assessmentType(AssessmentType.SEMI_AUTOMATIC);
        secondResultWithManualAssessment.setId(4L);

        submissionWithoutManualAssessment.setResults(List.of(firstResult));
        submissionWithManualAssessment.setResults(List.of(secondResult, retriggeredSecondResult, secondResultWithManualAssessment));
        Set<Submission> submissions = Set.of(submissionWithoutManualAssessment, submissionWithManualAssessment);
        submissions.forEach(s -> s.setParticipation(new StudentParticipation().exercise(programmingExercise)));
        var submission = submissionFilterService.getLatestSubmissionWithResult(submissions, false);
        assertThat(submission).isPresent().get().isEqualTo(submissionWithManualAssessment);
        assertThat(submission.get().getResults()).hasSize(1);
        assertThat(submission.get().getResults().getFirst()).isEqualTo(retriggeredSecondResult);
    }

    @Test
    void shouldFindLatestAutomaticProgrammingSubmissionManualAssessment_beforeAssessmentDueDate() {
        var programmingExercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusHours(3));

        var submissionWithManualAssessment = new ProgrammingSubmission();
        submissionWithManualAssessment.setId(2L);
        var firstResult = new Result().rated(true).completionDate(ZonedDateTime.now()).assessmentType(AssessmentType.AUTOMATIC);
        firstResult.setId(1L);
        var secondResult = new Result().rated(true).score(1.0).completionDate(ZonedDateTime.now().plusHours(1)).assessmentType(AssessmentType.AUTOMATIC);
        secondResult.setId(2L);
        var secondResultWithManualAssessment = new Result().score(2.0).rated(true).completionDate(ZonedDateTime.now().plusHours(2)).assessmentType(AssessmentType.SEMI_AUTOMATIC);
        secondResultWithManualAssessment.setId(4L);

        Result[] resultsArray = new Result[] { firstResult, secondResult, null, secondResultWithManualAssessment };

        submissionWithManualAssessment.setResults(Arrays.asList(resultsArray));
        Set<Submission> submissions = Set.of(submissionWithManualAssessment);
        submissions.forEach(s -> s.setParticipation(new StudentParticipation().exercise(programmingExercise)));
        var submission = submissionFilterService.getLatestSubmissionWithResult(submissions, false);
        assertThat(submission).isPresent().get().isEqualTo(submissionWithManualAssessment);
        assertThat(submission.get().getResults()).hasSize(1);
        assertThat(submission.get().getResults().getFirst()).isEqualTo(secondResult);
    }

    @Test
    void shouldNotFindProgrammingSubmission_beforeAssessmentDueDate_AutomaticResultDeleted() {
        var programmingExercise = exerciseByType.get(ExerciseType.PROGRAMMING);
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusHours(3));

        var submissionWithManualAssessment = new ProgrammingSubmission();
        submissionWithManualAssessment.setId(2L);
        var secondResultWithManualAssessment = new Result().score(2.0).rated(true).completionDate(ZonedDateTime.now().plusHours(2)).assessmentType(AssessmentType.SEMI_AUTOMATIC);

        submissionWithManualAssessment.setResults(List.of(secondResultWithManualAssessment));
        Set<Submission> submissions = Set.of(submissionWithManualAssessment);
        submissions.forEach(s -> s.setParticipation(new StudentParticipation().exercise(programmingExercise)));
        var submission = submissionFilterService.getLatestSubmissionWithResult(submissions, false);
        assertThat(submission).isEmpty();
    }

    /// QUIZ SUBMISSIONS ///

    @Test
    void shouldNotFindQuizSubmissionForExamExercise() {
        var quizExercise = exerciseByType.get(ExerciseType.QUIZ);
        // make it an exam exercise
        quizExercise.setExerciseGroup(new ExerciseGroup());
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        var participation = new StudentParticipation().exercise(quizExercise);
        var submission = new QuizSubmission();
        submission.setParticipation(participation);
        var optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(Set.of(submission), false);
        assertThat(optionalSubmission).isEmpty();
    }

    @Test
    void shouldNotFindQuizSubmissionWithResultThatHasNoDueDate() {
        var quizExercise = exerciseByType.get(ExerciseType.QUIZ);
        quizExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        var participation = new StudentParticipation().exercise(quizExercise);
        var submission = new QuizSubmission();
        submission.setParticipation(participation);
        submission.setResults(List.of(new Result()));
        var optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(Set.of(submission), false);
        assertThat(optionalSubmission).isEmpty();
    }

    private Submission getSubmissionBasedOnExerciseType(ExerciseType exerciseType) {
        return switch (exerciseType) {
            case TEXT -> new TextSubmission();
            case MODELING -> new ModelingSubmission();
            case QUIZ -> new QuizSubmission();
            case FILE_UPLOAD -> new FileUploadSubmission();
            case PROGRAMMING -> new ProgrammingSubmission();
        };
    }
}
