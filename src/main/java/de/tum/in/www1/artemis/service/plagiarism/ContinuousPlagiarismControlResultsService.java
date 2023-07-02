package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

/**
 * Manages results of checks executed during continuous plagiarism control.
 */
@Service
@Component
class ContinuousPlagiarismControlResultsService {

    private static final Logger log = LoggerFactory.getLogger(ContinuousPlagiarismControlResultsService.class);

    private static final String PLAGIARISM_RESULT_FEEDBACK = "Plagiarisms detected. Score reduced to 0.";

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    ContinuousPlagiarismControlResultsService(SubmissionRepository submissionRepository, ResultRepository resultRepository) {
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    void handleCpcResult(PlagiarismResult<?> result) {
        var submissionsIdsWithPlagiarism = result.getComparisons().stream().flatMap(it -> Stream.of(it.getSubmissionA().getSubmissionId(), it.getSubmissionB().getSubmissionId()))
                .collect(toUnmodifiableSet());

        result.getExercise().getStudentParticipations().stream().filter(participation -> participation.findLatestSubmission().isPresent()).forEach(participation -> {
            var submission = participation.findLatestSubmission().get();

            boolean plagiarismDetected = submissionsIdsWithPlagiarism.contains(submission.getId());
            submissionRepository.setPlagiarismDetected(plagiarismDetected, submission.getId());

            if (plagiarismDetected) {
                addResultWithPlagiarismFeedback(submission, participation);
            }
            else {
                deletePastResultsWithPlagiarismFeedback(submission.getId());
            }
        });
    }

    private void addResultWithPlagiarismFeedback(Submission submission, StudentParticipation participation) {
        log.debug("Adding cpc results for submission: submissionId={}.", submission.getId());

        var result = new Result();
        result.setSubmission(submission);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        result.setParticipation(participation);
        result.setRated(true);
        result.setExampleResult(false);
        result.setSuccessful(false);
        result.setScore(0.0, participation.getExercise().getCourseViaExerciseGroupOrCourseMember());

        var feedback = new Feedback();
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setPositive(false);
        feedback.setText(PLAGIARISM_RESULT_FEEDBACK);
        feedback.setResult(result);
        feedback.setVisibility(Visibility.ALWAYS);
        feedback.setCredits(0.0);
        result.setFeedbacks(List.of(feedback));

        resultRepository.save(result);
    }

    private void deletePastResultsWithPlagiarismFeedback(long submissionId) {
        log.debug("Removing cpc results for submission: submissionId={}.", submissionId);
        resultRepository.findAllWithFeedbackBySubmissionId(submissionId).stream()
                .filter(result -> result.getFeedbacks().stream().anyMatch(it -> it.getText().contains(PLAGIARISM_RESULT_FEEDBACK))).forEach(resultRepository::delete);
    }
}
