package de.tum.cit.aet.artemis.exam.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.AthenaFeedbackUsageDTO;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.modeling.api.ModelingFeedbackApi;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.text.api.TextFeedbackApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Handles Athena AI feedback requests for submitted test exams: dispatching feedback generation for eligible
 * text and modeling participations, and reporting how many requests a student has used against the configured cap.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class StudentExamAthenaFeedbackService {

    private final StudentExamRepository studentExamRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final Optional<TextFeedbackApi> textFeedbackApi;

    private final Optional<ModelingFeedbackApi> modelingFeedbackApi;

    /**
     * Maximum number of Athena feedback requests a student may accumulate across all of their submitted test-exam
     * attempts for a given exam. Reuses the course-exercise cap so the two stay in sync.
     */
    @Value("${artemis.athena.allowed-feedback-requests:10}")
    private int allowedFeedbackRequests;

    public StudentExamAthenaFeedbackService(StudentExamRepository studentExamRepository, StudentParticipationRepository studentParticipationRepository,
            Optional<TextFeedbackApi> textFeedbackApi, Optional<ModelingFeedbackApi> modelingFeedbackApi) {
        this.studentExamRepository = studentExamRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.textFeedbackApi = textFeedbackApi;
        this.modelingFeedbackApi = modelingFeedbackApi;
    }

    /**
     * Requests Athena AI feedback for all text and modeling participations of a submitted test exam whose course
     * has Athena formative feedback enabled. Called explicitly by the student via the test exam summary button.
     * <p>
     * Rejects the request if the student has already accumulated {@link #allowedFeedbackRequests} successful Athena
     * results across all of their test-exam attempts for this exam (cross-attempt cap), or if no exercise in the
     * attempt has Athena formative feedback enabled at the course level. Individual submissions that already have an
     * Athena result are skipped silently inside the async dispatch in {@code generateAutomaticFeedbackForTestExamAsync},
     * so remaining unassessed submissions in the same attempt still get processed.
     *
     * @param studentExam the submitted student exam
     * @param currentUser the user requesting feedback
     * @throws BadRequestAlertException if the exam is not a test exam, not submitted, Athena is unavailable, the
     *                                      request limit is reached, or no exercise has course-level Athena formative
     *                                      feedback enabled
     */
    public void requestAthenaFeedbackForTestExam(StudentExam studentExam, User currentUser) {
        if (!Boolean.TRUE.equals(studentExam.isSubmitted())) {
            throw new BadRequestAlertException("Student exam must be submitted before requesting feedback", "StudentExam", "studentExamNotSubmitted");
        }
        if (!studentExam.isTestExam()) {
            throw new BadRequestAlertException("Athena feedback is only available for test exams", "StudentExam", "notTestExam");
        }
        if (textFeedbackApi.isEmpty() && modelingFeedbackApi.isEmpty()) {
            throw new BadRequestAlertException("Athena feedback is not available", "StudentExam", "athenaNotAvailable");
        }

        // Approximate cap: count-and-dispatch is not transactional, so concurrent requests at used == cap - 1 can both pass and briefly exceed the cap by one.
        long attemptsWithAthenaResult = studentExamRepository.countTestExamAttemptsWithAthenaResultByUserIdAndExamId(currentUser.getId(), studentExam.getExam().getId());
        if (attemptsWithAthenaResult >= allowedFeedbackRequests) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "StudentExam", "maxAthenaResultsReached", true);
        }

        // Use studentExam exercises (course.athenaConfig eagerly loaded) to determine eligible exercise IDs,
        // avoiding lazy-load traversal through StudentParticipation.exercise.exerciseGroup.exam.course.athenaConfig.
        Set<Long> eligibleExerciseIds = studentExam.getExercises().stream().filter(Exercise::getAllowFeedbackRequests).map(Exercise::getId).collect(Collectors.toSet());
        if (eligibleExerciseIds.isEmpty()) {
            throw new BadRequestAlertException("No exam exercises with course-level Athena formative feedback enabled", "StudentExam", "noCourseLevelAthenaFormativeEnabled", true);
        }

        List<StudentParticipation> participations = studentParticipationRepository.findByStudentExamWithEagerLatestSubmissionResult(studentExam, false);
        List<StudentParticipation> eligibleParticipations = participations.stream()
                .filter(participation -> participation.getExercise() != null && eligibleExerciseIds.contains(participation.getExercise().getId())).toList();
        for (StudentParticipation participation : eligibleParticipations) {
            Exercise exercise = participation.getExercise();
            if (exercise instanceof TextExercise && textFeedbackApi.isEmpty()) {
                throw new BadRequestAlertException("Athena feedback for text exercises is not available", "StudentExam", "textAthenaNotAvailable");
            }
            if (exercise instanceof ModelingExercise && modelingFeedbackApi.isEmpty()) {
                throw new BadRequestAlertException("Athena feedback for modeling exercises is not available", "StudentExam", "modelingAthenaNotAvailable");
            }
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
     * Returns how many test-exam attempts of the given user have produced a successful Athena feedback result, paired
     * with the configured cap. Each attempt counts as one request regardless of how many exercises it contains.
     *
     * @param userId the id of the student whose test-exam attempts should be counted
     * @param examId the id of the exam the attempts belong to
     * @return the number of attempts that already produced an Athena result and the configured cap
     */
    public AthenaFeedbackUsageDTO getAthenaFeedbackUsage(Long userId, Long examId) {
        long used = studentExamRepository.countTestExamAttemptsWithAthenaResultByUserIdAndExamId(userId, examId);
        return new AthenaFeedbackUsageDTO(used, allowedFeedbackRequests);
    }
}
