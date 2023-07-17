package de.tum.in.www1.artemis.service.plagiarism;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

class ContinuousPlagiarismControlResultsServiceTest {

    private final SubmissionRepository submissionRepository = mock();

    private final ResultRepository resultRepository = mock();

    private final ContinuousPlagiarismControlResultsService service = new ContinuousPlagiarismControlResultsService(submissionRepository, resultRepository);

    @Test
    void shouldCorrectlySetPlagiarismDetectedAndAddResult() {
        // given: cpc result for exercise
        var result = buildPlagiarismResultWithSubmissionIds(1L, 2L);
        var exercise = buildExerciseWithSubmissionsIds(1L, 2L, 3L);
        result.setExercise(exercise);

        // and: existing past result for no plagiarism submission
        var pastResult = new Result();
        var feedback = new Feedback();
        feedback.setText("ContinuousPlagiarismControl: other feedback");
        pastResult.setFeedbacks(List.of(feedback));
        when(resultRepository.findAllWithFeedbackBySubmissionId(3L)).thenReturn(List.of(pastResult));

        // when
        service.handleCpcResult(result);

        // then
        verify(submissionRepository).setPlagiarismDetected(true, 1L);
        verify(submissionRepository).setPlagiarismDetected(true, 2L);
        verify(submissionRepository).setPlagiarismDetected(false, 3L);
        verify(resultRepository, times(2)).save(any());
        verify(resultRepository, times(1)).delete(pastResult);
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
    private static Exercise buildExerciseWithSubmissionsIds(long submission1Id, long submission2Id, long submission3Id) {
        var exercise = new TextExercise();
        var course = new Course();
        course.setAccuracyOfScores(1);
        exercise.setCourse(course);
        var participation1 = new StudentParticipation();
        var textSubmissionA = new TextSubmission();
        textSubmissionA.setId(submission1Id);
        participation1.setSubmissions(Set.of(textSubmissionA));
        participation1.setExercise(exercise);

        var participation2 = new StudentParticipation();
        var textSubmissionB = new TextSubmission();
        textSubmissionB.setId(submission2Id);
        participation2.setSubmissions(Set.of(textSubmissionB));
        participation2.setExercise(exercise);

        var participation3 = new StudentParticipation();
        var textSubmissionNoPlagiarism = new TextSubmission();
        textSubmissionNoPlagiarism.setId(submission3Id);
        participation3.setSubmissions(Set.of(textSubmissionNoPlagiarism));
        participation3.setExercise(exercise);

        exercise.setStudentParticipations(Set.of(participation1, participation2, participation3));

        return exercise;
    }
}
