package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableSet;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismChecksConfig;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

class ContinuousPlagiarismControlResultsServiceTest {

    private static final ArgumentMatcher<Result> isPlagiarismResult = it -> it.getFeedbacks().get(0).getText()
            .contains("Ändere deine Lösung vor Ablauf der Einreichungsfrist um dieses Problem zu beheben");

    private static final ArgumentMatcher<Result> isLimitExceededResult = it -> it.getFeedbacks().get(0).getText()
            .contains("You reached maximum number of submissions with plagiarism suspicion.");

    private final ResultRepository resultRepository = mock();

    private final StudentParticipationRepository participationRepository = mock();

    private final ContinuousPlagiarismControlResultsService service = new ContinuousPlagiarismControlResultsService(resultRepository, participationRepository);

    @Test
    void shouldIncrementCounterAndAddResultForNewPlagiarismSubmission() {
        // given: cpc result for exercise
        var result = buildPlagiarismResultWithSubmissionIds(1L, 2L);
        var exercise = buildExerciseWithSubmissionsIds(1L, 2L, null);
        result.setExercise(exercise);

        // and: no existing past result for plagiarism submissions
        when(resultRepository.findAllWithFeedbackBySubmissionId(any())).thenReturn(emptyList());

        // when
        service.handleCpcResult(result);

        // then
        verify(resultRepository, times(2)).save(argThat(isPlagiarismResult));
        verify(resultRepository, never()).delete(any());
        verify(participationRepository).incrementPlagiarismDetected(10L);
        verify(participationRepository).incrementPlagiarismDetected(20L);
    }

    @Test
    void shouldNotIncrementCounterAndAddResultForExistingPlagiarismSubmission() {
        // given: cpc result for exercise
        var result = buildPlagiarismResultWithSubmissionIds(1L, 2L);
        var exercise = buildExerciseWithSubmissionsIds(1L, 2L, null);
        result.setExercise(exercise);

        // and: existing past result for the same submissions
        var pastResult1 = buildResultsWithCpcFeedback(1L);
        when(resultRepository.findAllWithFeedbackBySubmissionId(1L)).thenReturn(List.of(pastResult1));
        var pastResult2 = buildResultsWithCpcFeedback(2L);
        when(resultRepository.findAllWithFeedbackBySubmissionId(2L)).thenReturn(List.of(pastResult2));

        // when
        service.handleCpcResult(result);

        // then
        verify(resultRepository, times(2)).save(argThat(isPlagiarismResult));
        verify(resultRepository, never()).delete(any());
        verify(participationRepository, never()).incrementPlagiarismDetected(anyLong());
    }

    @Test
    void shouldDeletePastPlagiarismResultsForNotPlagiarismSubmission() {
        // given: cpc result for exercise
        var result = buildPlagiarismResultWithSubmissionIds(2L, 3L);
        var exercise = buildExerciseWithSubmissionsIds(1L, null, null);
        result.setExercise(exercise);

        // and: existing past result for the first submission
        var pastResult = buildResultsWithCpcFeedback(1L);
        pastResult.setId(123L);
        when(resultRepository.findAllWithFeedbackBySubmissionId(1L)).thenReturn(List.of(pastResult));

        // when
        service.handleCpcResult(result);

        // then
        verify(resultRepository).delete(argThat(arg -> arg.getId().equals(123L)));
        verify(participationRepository, never()).incrementPlagiarismDetected(anyLong());
    }

    @Test
    void shouldAddDetectionLimitExceedResult() {
        // given: cpc result for exercise
        var result = buildPlagiarismResultWithSubmissionIds(1L, 2L);
        var exercise = buildExerciseWithSubmissionsIds(1L, 2L, 3L);
        exercise.getStudentParticipations().forEach(it -> it.setNumberOfCpcPlagiarismDetections(999));
        result.setExercise(exercise);

        // and: existing past result for the first submission
        var pastResult1 = buildResultsWithCpcFeedback(1L);
        when(resultRepository.findAllWithFeedbackBySubmissionId(1L)).thenReturn(List.of(pastResult1));
        when(resultRepository.findAllWithFeedbackBySubmissionId(2L)).thenReturn(emptyList());
        when(resultRepository.findAllWithFeedbackBySubmissionId(3L)).thenReturn(emptyList());

        // when
        service.handleCpcResult(result);

        // then
        verify(resultRepository, times(3)).save(argThat(isLimitExceededResult));
        verify(resultRepository, never()).delete(any());
        verify(participationRepository, never()).incrementPlagiarismDetected(anyLong());
    }

    private Result buildResultsWithCpcFeedback(long submissionId) {
        var submission = new TextSubmission();
        submission.setId(submissionId);

        var feedback = new Feedback();
        feedback.setText("Continuous Plagiarism Control: other feedback");

        var result = new Result();
        result.setSubmission(submission);
        result.setFeedbacks(List.of(feedback));
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static TextPlagiarismResult buildPlagiarismResultWithSubmissionIds(long submissionAId, long submissionBId) {
        var result = new TextPlagiarismResult();

        var comparison = new PlagiarismComparison<TextSubmissionElement>();
        var submissionA = new PlagiarismSubmission<>();
        submissionA.setSubmissionId(submissionAId);
        comparison.setSubmissionA(submissionA);
        var submissionB = new PlagiarismSubmission<>();
        submissionB.setSubmissionId(submissionBId);
        comparison.setSubmissionB(submissionB);
        result.setComparisons(Set.of(comparison));

        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static Exercise buildExerciseWithSubmissionsIds(long submission1Id, Long submission2Id, Long submission3Id) {
        var exercise = new TextExercise();
        var course = new Course();
        course.setAccuracyOfScores(1);
        exercise.setCourse(course);

        var participations = new HashSet<StudentParticipation>();

        var participation1 = new StudentParticipation();
        var textSubmissionA = new TextSubmission();
        textSubmissionA.setId(submission1Id);
        participation1.setId(10L);
        participation1.setSubmissions(Set.of(textSubmissionA));
        participation1.setExercise(exercise);
        participations.add(participation1);

        if (submission2Id != null) {
            var participation2 = new StudentParticipation();
            var textSubmissionB = new TextSubmission();
            textSubmissionB.setId(submission2Id);
            participation2.setId(20L);
            participation2.setSubmissions(Set.of(textSubmissionB));
            participation2.setExercise(exercise);
            participations.add(participation2);
        }

        if (submission3Id != null) {
            var participation3 = new StudentParticipation();
            var textSubmissionNoPlagiarism = new TextSubmission();
            textSubmissionNoPlagiarism.setId(submission3Id);
            participation3.setId(30L);
            participation3.setSubmissions(Set.of(textSubmissionNoPlagiarism));
            participation3.setExercise(exercise);
            participations.add(participation3);
        }

        exercise.setStudentParticipations(unmodifiableSet(participations));

        var plagiarismChecksConfig = PlagiarismChecksConfig.createDefault();
        exercise.setPlagiarismChecksConfig(plagiarismChecksConfig);

        return exercise;
    }
}
