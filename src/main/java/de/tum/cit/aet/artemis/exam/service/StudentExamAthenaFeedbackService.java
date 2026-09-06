package de.tum.cit.aet.artemis.exam.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.AthenaFeedbackUsageDTO;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.modeling.api.ModelingFeedbackApi;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.text.api.TextFeedbackApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Handles Athena AI feedback requests for submitted test exams and instructor test runs: dispatching feedback
 * generation for eligible text and modeling participations, and reporting how many requests a user has already used
 * against the configured cap.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class StudentExamAthenaFeedbackService {

    private final StudentExamRepository studentExamRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final Optional<TextFeedbackApi> textFeedbackApi;

    private final Optional<ModelingFeedbackApi> modelingFeedbackApi;

    private final Optional<AthenaFeedbackApi> athenaFeedbackApi;

    /**
     * Maximum number of Athena feedback requests a user may accumulate across all of their submitted test-exam
     * attempts (or, for an instructor, all of their test runs) for a given exam. Reuses the course-exercise cap so the
     * two stay in sync.
     */
    @Value("${artemis.athena.allowed-feedback-requests:10}")
    private int allowedFeedbackRequests;

    public StudentExamAthenaFeedbackService(StudentExamRepository studentExamRepository, StudentParticipationRepository studentParticipationRepository,
            Optional<TextFeedbackApi> textFeedbackApi, Optional<ModelingFeedbackApi> modelingFeedbackApi, Optional<AthenaFeedbackApi> athenaFeedbackApi) {
        this.studentExamRepository = studentExamRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.textFeedbackApi = textFeedbackApi;
        this.modelingFeedbackApi = modelingFeedbackApi;
        this.athenaFeedbackApi = athenaFeedbackApi;
    }

    /**
     * Requests Athena AI feedback for all text and modeling participations of a submitted test exam or test run whose
     * course has Athena formative feedback enabled. Called explicitly via the button on the exam summary - by the
     * student for a test exam attempt, by the instructor for a test run.
     * <p>
     * Rejects the request if the user has already reserved {@link #allowedFeedbackRequests} attempts against the
     * cross-attempt cap for this exam, or if no exercise in the attempt has Athena formative feedback enabled at the
     * course level. The cap check and the reservation of this attempt's slot happen atomically in a single database
     * transaction (see {@link StudentExamRepository#reserveAthenaFeedbackRequestIfBelowCap}), so concurrent requests -
     * whether for this attempt or another attempt of the same user/exam - cannot both observe a cap that has not yet
     * been reached. Participations whose latest submission already has an Athena result are excluded up front (see
     * {@link #isEligibleForAthenaFeedback}), so a repeated or recovery request cannot consume a cap slot without
     * dispatching any new generation; only the resulting still-eligible submissions are dispatched to
     * {@code generateAutomaticFeedbackForTestExamAsync}.
     *
     * @param studentExam the submitted student exam
     * @param currentUser the user requesting feedback
     * @throws BadRequestAlertException if the attempt is neither a test exam nor a test run, not submitted, Athena is
     *                                      unavailable, the request limit is reached, or no exercise has course-level
     *                                      Athena formative feedback enabled
     */
    public void requestAthenaFeedback(StudentExam studentExam, User currentUser) {
        if (!Boolean.TRUE.equals(studentExam.isSubmitted())) {
            throw new BadRequestAlertException("Student exam must be submitted before requesting feedback", "StudentExam", "studentExamNotSubmitted");
        }
        // Test runs are an instructor's own rehearsal of a real exam, so they get the same formative feedback as a
        // student's test-exam attempt. Regular attempts of a real exam are excluded: those are graded by the course.
        if (!studentExam.isTestExam() && !studentExam.isTestRun()) {
            throw new BadRequestAlertException("Athena feedback is only available for test exams and test runs", "StudentExam", "notTestExam");
        }
        if (athenaFeedbackApi.isEmpty() || (textFeedbackApi.isEmpty() && modelingFeedbackApi.isEmpty())) {
            throw new BadRequestAlertException("Athena feedback is not available", "StudentExam", "athenaNotAvailable");
        }

        // Use studentExam exercises (course.athenaConfig eagerly loaded) to determine eligible exercise IDs,
        // avoiding lazy-load traversal through StudentParticipation.exercise.exerciseGroup.exam.course.athenaConfig.
        // Only text and modeling exercises are dispatched below, so exclude other exercise types here even if their
        // course has formative feedback enabled - otherwise a request could reserve a cap slot without ever
        // generating feedback.
        Set<Long> eligibleExerciseIds = studentExam.getExercises().stream()
                .filter(exercise -> (exercise instanceof TextExercise || exercise instanceof ModelingExercise) && exercise.getAllowFeedbackRequests()).map(Exercise::getId)
                .collect(Collectors.toSet());
        if (eligibleExerciseIds.isEmpty()) {
            throw new BadRequestAlertException("No exam exercises with course-level Athena formative feedback enabled", "StudentExam", "noCourseLevelAthenaFormativeEnabled", true);
        }

        List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(studentExam, false);
        // Exclude participations whose latest submission is empty or already has an Athena result: the feedback
        // generators below skip both cases silently, so including them here would consume a cap slot without ever
        // generating new feedback.
        List<StudentParticipation> eligibleParticipations = participations.stream()
                .filter(participation -> participation.getExercise() != null && eligibleExerciseIds.contains(participation.getExercise().getId()))
                .filter(this::isEligibleForAthenaFeedback).toList();
        if (eligibleParticipations.isEmpty()) {
            throw new BadRequestAlertException("No exam exercises with course-level Athena formative feedback enabled", "StudentExam", "noCourseLevelAthenaFormativeEnabled", true);
        }

        for (StudentParticipation participation : eligibleParticipations) {
            Exercise exercise = participation.getExercise();
            if (exercise instanceof TextExercise && textFeedbackApi.isEmpty()) {
                throw new BadRequestAlertException("Athena feedback for text exercises is not available", "StudentExam", "textAthenaNotAvailable");
            }
            if (exercise instanceof ModelingExercise && modelingFeedbackApi.isEmpty()) {
                throw new BadRequestAlertException("Athena feedback for modeling exercises is not available", "StudentExam", "modelingAthenaNotAvailable");
            }
        }

        // Reserve this attempt's slot only now that the request is known to actually dispatch generation: reserving any
        // earlier would burn a cap slot on a request that fails validation and never generates anything.
        int reserved = studentExamRepository.reserveAthenaFeedbackRequestIfBelowCap(studentExam.getId(), currentUser.getId(), studentExam.getExam().getId(),
                studentExam.isTestRun(), ZonedDateTime.now(), allowedFeedbackRequests);
        if (reserved == 0) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "StudentExam", "maxAthenaResultsReached", true);
        }

        for (StudentParticipation participation : eligibleParticipations) {
            Exercise exercise = participation.getExercise();
            if (exercise instanceof TextExercise textExercise) {
                textFeedbackApi.ifPresent(api -> api.generateAutomaticFeedbackForTestExamAsync(participation, textExercise));
            }
            else if (exercise instanceof ModelingExercise modelingExercise) {
                modelingFeedbackApi.ifPresent(api -> api.generateAutomaticFeedbackForTestExamAsync(participation, modelingExercise));
            }
        }
    }

    /**
     * Returns how many attempts of the given user have reserved an Athena feedback request, paired with the configured
     * cap. Each attempt counts as one request regardless of how many exercises it contains. Test runs are counted
     * separately from test-exam attempts, matching how the cap is reserved.
     *
     * @param userId  the id of the user whose attempts should be counted
     * @param examId  the id of the exam the attempts belong to
     * @param testRun whether the test-run attempts (true) or the test-exam attempts (false) should be counted
     * @return the number of attempts that already reserved an Athena feedback request and the configured cap
     */
    public AthenaFeedbackUsageDTO getAthenaFeedbackUsage(Long userId, Long examId, boolean testRun) {
        long used = studentExamRepository.countAttemptsWithAthenaFeedbackRequestedByUserIdAndExamId(userId, examId, testRun);
        return new AthenaFeedbackUsageDTO(used, allowedFeedbackRequests);
    }

    /**
     * Determines whether the participation's latest submission is a non-empty text or modeling submission without an
     * existing Athena result, i.e. one that the corresponding feedback generator will actually process instead of
     * skipping.
     */
    private boolean isEligibleForAthenaFeedback(StudentParticipation participation) {
        Optional<Submission> latestSubmission = participation.findLatestSubmission();
        if (latestSubmission.isEmpty()) {
            return false;
        }
        Submission submission = latestSubmission.get();
        boolean nonEmptySupportedSubmission = (submission instanceof TextSubmission textSubmission && !textSubmission.isEmpty())
                || (submission instanceof ModelingSubmission modelingSubmission && !modelingSubmission.isEmpty());
        if (!nonEmptySupportedSubmission) {
            return false;
        }
        return athenaFeedbackApi.map(api -> !api.submissionHasAthenaResult(submission)).orElse(true);
    }
}
