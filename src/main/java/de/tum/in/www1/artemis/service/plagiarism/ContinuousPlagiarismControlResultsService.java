package de.tum.in.www1.artemis.service.plagiarism;

import static de.tum.in.www1.artemis.service.plagiarism.ContinuousPlagiarismControlFeedbackHelper.CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_DE;
import static de.tum.in.www1.artemis.service.plagiarism.ContinuousPlagiarismControlFeedbackHelper.CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_EN;
import static java.lang.String.format;
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

/**
 * Manages results of checks executed during continuous plagiarism control.
 */
@Service
@Component
class ContinuousPlagiarismControlResultsService {

    private static final Logger log = LoggerFactory.getLogger(ContinuousPlagiarismControlResultsService.class);

    private static final String CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_TEXT_EN = format(
            "%s Suspicion of plagiarism! Score reduced to 0. To fix this issue modify your submission before the due date of the exercise.",
            CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_EN);

    private static final String CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_TEXT_DE = format(
            "%s Verdacht auf Plagiat! Punktestand auf 0 reduziert. Ändere deine Lösung vor Ablauf der Einreichungsfrist um dieses Problem zu beheben",
            CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_DE);

    private static final String CONTINUOUS_PLAGIARISM_CONTROL_LIMIT_EXCEEDED_FEEDBACK_TEXT_EN = format(
            "%s Suspicion of plagiarism! Score reduced to 0. You reached maximum number of submissions with plagiarism suspicion. You further submissions will not be graded.",
            CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_EN);

    private static final String CONTINUOUS_PLAGIARISM_CONTROL_LIMIT_EXCEEDED_FEEDBACK_TEXT_DE = format(
            "%s Verdacht auf Plagiat! Punktestand auf 0 reduziert. You reached maximum number of submissions with plagiarism suspicion.",
            CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_DE);

    private final ResultRepository resultRepository;

    private final StudentParticipationRepository participationRepository;

    private static final Predicate<Result> isPlagiarismResult = result -> result.getFeedbacks().stream().map(Feedback::getText)
            .anyMatch(it -> it.startsWith(CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_EN) || it.startsWith(CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_IDENTIFIER_DE));

    ContinuousPlagiarismControlResultsService(ResultRepository resultRepository, StudentParticipationRepository participationRepository) {
        this.resultRepository = resultRepository;
        this.participationRepository = participationRepository;
    }

    void handleCpcResult(PlagiarismResult<?> result) {
        var submissionsIdsWithPlagiarism = result.getComparisons().stream().flatMap(it -> Stream.of(it.getSubmissionA().getSubmissionId(), it.getSubmissionB().getSubmissionId()))
                .collect(toUnmodifiableSet());

        result.getExercise().getStudentParticipations().stream().filter(participation -> participation.findLatestSubmission().isPresent()).forEach(participation -> {
            var submission = participation.findLatestSubmission().get();

            boolean plagiarismDetected = submissionsIdsWithPlagiarism.contains(submission.getId());
            boolean plagiarismDetectedBefore = resultRepository.findAllWithFeedbackBySubmissionId(submission.getId()).stream()
                    .filter(it -> it.getSubmission().getId().equals(submission.getId())).anyMatch(isPlagiarismResult);
            boolean plagiarismDetectionLimitExceeded = Objects.requireNonNullElse(participation.getNumberOfCpcPlagiarismDetections(), 0) >= participation.getExercise()
                    .getPlagiarismChecksConfig().getContinuousPlagiarismControlDetectionsLimit();

            if (plagiarismDetectionLimitExceeded) {
                addResultWithLimitExceededFeedback(submission, participation);
            }
            else if (plagiarismDetected && plagiarismDetectedBefore) {
                addResultWithPlagiarismFeedback(submission, participation);
            }
            else if (plagiarismDetected) {
                incrementPlagiarismDetectionCounter(participation);
                addResultWithPlagiarismFeedback(submission, participation);
            }
            else {
                deletePastResultsWithPlagiarismFeedback(submission.getId());
            }
        });
    }

    private void incrementPlagiarismDetectionCounter(StudentParticipation participation) {
        participationRepository.incrementPlagiarismDetected(participation.getId());
    }

    private void addResultWithLimitExceededFeedback(Submission submission, StudentParticipation participation) {
        log.debug("Adding cpc limit exceeded result for submission: submissionId={}.", submission.getId());
        var feedbackText = getLimitExceededFeedbackTextForStudent(participation);
        var result = createCpcResult(submission, participation, feedbackText);
        resultRepository.save(result);
    }

    private void addResultWithPlagiarismFeedback(Submission submission, StudentParticipation participation) {
        log.debug("Adding cpc result for submission: submissionId={}.", submission.getId());
        var feedbackText = getFeedbackTextForStudent(participation);
        var result = createCpcResult(submission, participation, feedbackText);
        resultRepository.save(result);
    }

    private static Result createCpcResult(Submission submission, StudentParticipation participation, String feedbackText) {
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
        feedback.setResult(result);
        feedback.setVisibility(Visibility.ALWAYS);
        feedback.setCredits(0.0);
        feedback.setText(feedbackText);

        result.setFeedbacks(List.of(feedback));

        return result;
    }

    private static String getFeedbackTextForStudent(StudentParticipation participation) {
        return participation.getStudent().map(User::getLangKey).map(Locale::forLanguageTag).filter(isEqual(Locale.ENGLISH))
                .map(it -> CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_TEXT_EN).orElse(CONTINUOUS_PLAGIARISM_CONTROL_FEEDBACK_TEXT_DE);
    }

    private static String getLimitExceededFeedbackTextForStudent(StudentParticipation participation) {
        return participation.getStudent().map(User::getLangKey).map(Locale::forLanguageTag).filter(isEqual(Locale.ENGLISH))
                .map(it -> CONTINUOUS_PLAGIARISM_CONTROL_LIMIT_EXCEEDED_FEEDBACK_TEXT_EN).orElse(CONTINUOUS_PLAGIARISM_CONTROL_LIMIT_EXCEEDED_FEEDBACK_TEXT_DE);
    }

    private void deletePastResultsWithPlagiarismFeedback(long submissionId) {
        log.debug("Removing cpc results for submission: submissionId={}.", submissionId);
        resultRepository.findAllWithFeedbackBySubmissionId(submissionId).stream().filter(isPlagiarismResult).forEach(resultRepository::delete);
    }
}
