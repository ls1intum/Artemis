package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.CourseNotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.ReactionRepository;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingSpecificationRepository;
import de.tum.cit.aet.artemis.core.domain.CourseOperationType;
import de.tum.cit.aet.artemis.core.dto.CourseSummaryDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.user.UserService;
import de.tum.cit.aet.artemis.exam.api.ExamDeletionApi;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.dto.ExamDeletionInfoDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionInfoDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

/**
 * Service for resetting a course by deleting all student data while preserving the course structure.
 * This is useful for data privacy compliance (e.g., GDPR) or reusing course templates.
 * <p>
 * <b>Data that is PRESERVED (kept after reset):</b>
 * <table border="1">
 * <tr>
 * <th>Category</th>
 * <th>Data Type</th>
 * </tr>
 * <tr>
 * <td>Course Structure</td>
 * <td>Course settings, exercises, exams, lectures, competencies, tutorial group definitions</td>
 * </tr>
 * <tr>
 * <td>Communication</td>
 * <td>Channel/conversation structure (but not messages)</td>
 * </tr>
 * <tr>
 * <td>Staff</td>
 * <td>Instructor assignments only</td>
 * </tr>
 * </table>
 * <p>
 * <b>Data that is DELETED (removed during reset):</b>
 * <table border="1">
 * <tr>
 * <th>Category</th>
 * <th>Data Type</th>
 * </tr>
 * <tr>
 * <td>Exercise Data</td>
 * <td>Participations, submissions, results, feedbacks, build results, plagiarism cases</td>
 * </tr>
 * <tr>
 * <td>Exam Data</td>
 * <td>Student exams, exam participations, exam submissions, exam grades</td>
 * </tr>
 * <tr>
 * <td>Learning Analytics</td>
 * <td>Competency progress, learner profiles, participant scores</td>
 * </tr>
 * <tr>
 * <td>Communication</td>
 * <td>Posts, answer posts, reactions, notifications, notification settings</td>
 * </tr>
 * <tr>
 * <td>AI Features</td>
 * <td>Iris chat sessions, LLM token usage traces</td>
 * </tr>
 * <tr>
 * <td>Tutorial Groups</td>
 * <td>Tutorial group registrations (student assignments to groups)</td>
 * </tr>
 * <tr>
 * <td>Enrollment</td>
 * <td>Students, tutors, and editors removed from their groups</td>
 * </tr>
 * </table>
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseResetService {

    private static final Logger log = LoggerFactory.getLogger(CourseResetService.class);

    private static final int TOTAL_RESET_STEPS = 11;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ExerciseRepository exerciseRepository;

    private final Optional<ExamDeletionApi> examDeletionApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final ReactionRepository reactionRepository;

    private final AnswerPostRepository answerPostRepository;

    private final PostRepository postRepository;

    private final CourseNotificationRepository courseNotificationRepository;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final UserService userService;

    private final CourseOperationProgressService progressService;

    private final CourseAdminService courseAdminService;

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    public CourseResetService(ExerciseDeletionService exerciseDeletionService, ExerciseRepository exerciseRepository, Optional<ExamDeletionApi> examDeletionApi,
            Optional<ExamRepositoryApi> examRepositoryApi, Optional<CompetencyProgressApi> competencyProgressApi, Optional<LearnerProfileApi> learnerProfileApi,
            Optional<IrisSettingsApi> irisSettingsApi, Optional<TutorialGroupApi> tutorialGroupApi, ReactionRepository reactionRepository,
            AnswerPostRepository answerPostRepository, PostRepository postRepository, CourseNotificationRepository courseNotificationRepository,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository, LLMTokenUsageTraceRepository llmTokenUsageTraceRepository,
            CourseRepository courseRepository, UserRepository userRepository, UserService userService, CourseOperationProgressService progressService,
            CourseAdminService courseAdminService, ParticipationRepository participationRepository, SubmissionRepository submissionRepository) {
        this.exerciseDeletionService = exerciseDeletionService;
        this.exerciseRepository = exerciseRepository;
        this.examDeletionApi = examDeletionApi;
        this.examRepositoryApi = examRepositoryApi;
        this.competencyProgressApi = competencyProgressApi;
        this.learnerProfileApi = learnerProfileApi;
        this.irisSettingsApi = irisSettingsApi;
        this.tutorialGroupApi = tutorialGroupApi;
        this.reactionRepository = reactionRepository;
        this.answerPostRepository = answerPostRepository;
        this.postRepository = postRepository;
        this.courseNotificationRepository = courseNotificationRepository;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.progressService = progressService;
        this.courseAdminService = courseAdminService;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Resets all student data for a course while preserving the course structure.
     * This removes all student, tutor, and editor data while keeping instructor assignments.
     * Progress is reported via WebSocket for real-time UI updates using weighted progress
     * that accounts for the complexity of each operation.
     *
     * @param courseId the ID of the course to reset
     */
    public void resetStudentData(long courseId) {
        log.info("Resetting all student data for course {}", courseId);
        ZonedDateTime startedAt = ZonedDateTime.now();
        int stepsCompleted = 0;

        // Calculate weighted progress based on course content
        CourseSummaryDTO summary = courseAdminService.getCourseSummary(courseId);

        // Calculate actual exam weight based on real exam data (student exams, programming exercises)
        // Apply 0.5 factor for reset (structure preserved, only student data deleted)
        List<ExamDeletionInfoDTO> examInfoList = examRepositoryApi.map(api -> api.findDeletionInfoByCourseId(courseId)).orElse(List.of());
        double actualExamWeight = examInfoList.stream()
                .mapToDouble(info -> CourseOperationWeights.calculateExamWeight(info.studentExamCount(), info.programmingExerciseCount()) * 0.5).sum();

        double totalWeight = CourseOperationWeights.calculateResetTotalWeight(summary, actualExamWeight);
        double completedWeight = 0;

        try {
            progressService.startOperation(courseId, CourseOperationType.RESET, "Resetting exercises", TOTAL_RESET_STEPS);

            // Step 1: Reset exercises (with per-exercise progress updates)
            completedWeight = resetExercisesWithWeightedProgress(courseId, stepsCompleted, startedAt, completedWeight, totalWeight);
            stepsCompleted++;

            // Step 2: Reset exams (with per-exam progress updates)
            completedWeight = resetExamsWithWeightedProgress(courseId, examInfoList, stepsCompleted, startedAt, completedWeight, totalWeight);
            stepsCompleted++;

            // Step 3: Delete competency progress
            double competencyProgressWeight = summary.numberOfCompetencyProgress() * CourseOperationWeights.getWeightPerCompetencyProgress();
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting competency progress", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteCompetencyProgress(courseId);
            completedWeight += competencyProgressWeight;
            stepsCompleted++;

            // Step 4: Delete learner profiles
            double learnerProfileWeight = summary.numberOfLearnerProfiles() * CourseOperationWeights.getWeightPerLearnerProfile();
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting learner profiles", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteCourseLearnerProfiles(courseId);
            completedWeight += learnerProfileWeight;
            stepsCompleted++;

            // Step 5: Delete posts from conversations
            double postsWeight = summary.numberOfPosts() * CourseOperationWeights.getWeightPerPost() + summary.numberOfAnswerPosts() * CourseOperationWeights.getWeightPerAnswer();
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting posts", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deletePostsFromConversations(courseId);
            completedWeight += postsWeight;
            stepsCompleted++;

            // Step 6: Delete notifications
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting notifications", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteNotifications(courseId);
            completedWeight += CourseOperationWeights.getWeightNotifications();
            stepsCompleted++;

            // Step 7: Delete notification settings
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting notification settings", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteNotificationSettings(courseId);
            completedWeight += CourseOperationWeights.getWeightNotificationSettings();
            stepsCompleted++;

            // Step 8: Delete Iris data
            double irisWeight = summary.numberOfIrisChatSessions() * CourseOperationWeights.getWeightPerIrisSession();
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting Iris chat sessions", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteIrisData(courseId);
            completedWeight += irisWeight;
            stepsCompleted++;

            // Step 9: Delete LLM token usage traces
            double llmWeight = summary.numberOfLLMTraces() * CourseOperationWeights.getWeightPerLlmTrace();
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting LLM usage traces", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteLLMTokenUsageTraces(courseId);
            completedWeight += llmWeight;
            stepsCompleted++;

            // Step 10: Delete tutorial group registrations
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Deleting tutorial group registrations", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteTutorialGroupRegistrations(courseId);
            completedWeight += CourseOperationWeights.getWeightTutorialRegistrations();
            stepsCompleted++;

            // Step 11: Unenroll students, tutors, and editors
            long usersToUnenroll = summary.numberOfStudents() + summary.numberOfTutors() + summary.numberOfEditors();
            double unenrollWeight = usersToUnenroll * CourseOperationWeights.getWeightPerUserUnenroll();
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Unenrolling users", stepsCompleted, TOTAL_RESET_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            unenrollStudentsTutorsAndEditors(courseId);
            completedWeight += unenrollWeight;
            stepsCompleted++;

            progressService.completeOperation(courseId, CourseOperationType.RESET, TOTAL_RESET_STEPS, 0, startedAt);
            log.info("Successfully reset all student data for course {}", courseId);
        }
        catch (Exception e) {
            log.error("Failed to reset course {}", courseId, e);
            progressService.failOperation(courseId, CourseOperationType.RESET, "Reset failed", stepsCompleted, TOTAL_RESET_STEPS, 0, startedAt, e.getMessage(),
                    calculateProgressPercent(completedWeight, totalWeight));
            throw e;
        }
    }

    private double calculateProgressPercent(double completedWeight, double totalWeight) {
        if (totalWeight <= 0) {
            return 0;
        }
        return Math.min(100.0, (completedWeight / totalWeight) * 100.0);
    }

    /**
     * Resets exercises with weighted progress reporting for individual exercise reset operations.
     * Each exercise's weight is calculated based on its type and the number of participations/submissions.
     *
     * @param courseId        the course ID
     * @param stepsCompleted  the number of steps completed so far
     * @param startedAt       when the operation started
     * @param completedWeight the weight of already completed operations
     * @param totalWeight     the total weight for the entire reset
     * @return the updated completed weight after resetting all exercises
     */
    private double resetExercisesWithWeightedProgress(long courseId, int stepsCompleted, ZonedDateTime startedAt, double completedWeight, double totalWeight) {
        Set<ExerciseDeletionInfoDTO> exercises = exerciseRepository.findDeletionInfoByCourseId(courseId);
        int totalExercises = exercises.size();
        int processed = 0;

        for (ExerciseDeletionInfoDTO exercise : exercises) {
            // Calculate weight for this specific exercise (slightly less than delete since structure preserved)
            long participations = participationRepository.countByExerciseId(exercise.id());
            long submissions = submissionRepository.countByExerciseId(exercise.id());

            double exerciseWeight;
            if (exercise.isProgrammingExercise()) {
                exerciseWeight = CourseOperationWeights.calculateProgrammingExerciseWeight(participations, submissions) * 0.7;
            }
            else {
                exerciseWeight = CourseOperationWeights.calculateOtherExerciseWeight(participations, submissions) * 0.5;
            }

            // Report progress before resetting this exercise
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Resetting exercise: " + exercise.title(), stepsCompleted, TOTAL_RESET_STEPS, processed,
                    totalExercises, 0, startedAt, calculateProgressPercent(completedWeight, totalWeight));

            exerciseDeletionService.reset(exercise.id());
            completedWeight += exerciseWeight;
            processed++;

            // Report progress after resetting
            progressService.updateProgress(courseId, CourseOperationType.RESET, "Resetting exercises", stepsCompleted, TOTAL_RESET_STEPS, processed, totalExercises, 0, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
        }

        return completedWeight;
    }

    /**
     * Resets exams with weighted progress reporting for individual exam reset operations.
     *
     * @param courseId        the course ID
     * @param examInfoList    pre-fetched exam deletion info (to avoid redundant queries)
     * @param stepsCompleted  the number of steps completed so far
     * @param startedAt       when the operation started
     * @param completedWeight the weight of already completed operations
     * @param totalWeight     the total weight for the entire reset
     * @return the updated completed weight after resetting all exams
     */
    private double resetExamsWithWeightedProgress(long courseId, List<ExamDeletionInfoDTO> examInfoList, int stepsCompleted, ZonedDateTime startedAt, double completedWeight,
            double totalWeight) {
        if (examDeletionApi.isEmpty()) {
            return completedWeight;
        }

        int totalExams = examInfoList.size();
        int processed = 0;

        for (ExamDeletionInfoDTO examInfo : examInfoList) {
            double examWeight = CourseOperationWeights.calculateExamWeight(examInfo.studentExamCount(), examInfo.programmingExerciseCount()) * 0.5; // Less weight for reset vs
                                                                                                                                                    // delete

            progressService.updateProgress(courseId, CourseOperationType.RESET, "Resetting exam", stepsCompleted, TOTAL_RESET_STEPS, processed, totalExams, 0, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));

            examDeletionApi.get().reset(examInfo.examId());
            completedWeight += examWeight;
            processed++;

            progressService.updateProgress(courseId, CourseOperationType.RESET, "Resetting exams", stepsCompleted, TOTAL_RESET_STEPS, processed, totalExams, 0, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
        }

        return completedWeight;
    }

    /**
     * Deletes all competency progress records for students in the course.
     * The competency definitions themselves are preserved.
     *
     * @param courseId the ID of the course whose competency progress should be deleted
     */
    private void deleteCompetencyProgress(long courseId) {
        competencyProgressApi.ifPresent(api -> api.deleteAllByCourseId(courseId));
    }

    /**
     * Deletes all course-specific learner profiles for students in the course.
     * This removes learning analytics data collected for the course.
     *
     * @param courseId the ID of the course whose learner profiles should be deleted
     */
    private void deleteCourseLearnerProfiles(long courseId) {
        learnerProfileApi.ifPresent(api -> api.deleteAllForCourseId(courseId));
    }

    /**
     * Deletes all posts and messages from conversations in the course while preserving
     * the conversation/channel structure. Deletion is performed in the correct order
     * (reactions -> answer posts -> posts) to handle foreign key constraints,
     * as bulk delete queries bypass JPA cascade behavior.
     *
     * @param courseId the ID of the course whose posts should be deleted
     */
    private void deletePostsFromConversations(long courseId) {
        // Delete in correct order: reactions first, then answers, then posts
        // This is necessary because bulk delete queries bypass JPA cascade
        reactionRepository.deleteAllByAnswerPostCourseId(courseId);
        reactionRepository.deleteAllByPostCourseId(courseId);
        answerPostRepository.deleteAllByCourseId(courseId);
        postRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes all course notifications for the course.
     *
     * @param courseId the ID of the course whose notifications should be deleted
     */
    private void deleteNotifications(long courseId) {
        courseNotificationRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes all user notification settings (presets and specifications) for the course.
     * This removes user preferences for how they receive notifications in this course.
     *
     * @param courseId the ID of the course whose notification settings should be deleted
     */
    private void deleteNotificationSettings(long courseId) {
        userCourseNotificationSettingPresetRepository.deleteAllByCourseId(courseId);
        userCourseNotificationSettingSpecificationRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes all Iris AI tutor chat sessions for the course.
     * This removes the conversation history between students and the AI tutor.
     *
     * @param courseId the ID of the course whose Iris chat sessions should be deleted
     */
    private void deleteIrisData(long courseId) {
        irisSettingsApi.ifPresent(api -> api.deleteCourseChatSessions(courseId));
    }

    /**
     * Deletes all LLM token usage traces for the course.
     * This removes records of AI model usage (tokens consumed, costs) for the course.
     *
     * @param courseId the ID of the course whose LLM traces should be deleted
     */
    private void deleteLLMTokenUsageTraces(long courseId) {
        llmTokenUsageTraceRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes all tutorial group registrations for the course.
     * The tutorial group definitions themselves are preserved.
     *
     * @param courseId the ID of the course whose tutorial group registrations should be deleted
     */
    private void deleteTutorialGroupRegistrations(long courseId) {
        tutorialGroupApi.ifPresent(api -> api.deleteAllRegistrationsByCourseId(courseId));
    }

    /**
     * Unenrolls all students, tutors, and editors from the course by removing them from their groups.
     * Only instructors are preserved.
     * <p>
     * This method uses bulk database operations to efficiently remove group associations
     * in a single query per group, rather than loading and saving each user individually.
     * The user cache is properly evicted for all affected users.
     *
     * @param courseId the ID of the course whose students, tutors, and editors should be unenrolled
     */
    private void unenrollStudentsTutorsAndEditors(long courseId) {
        // Remove students using bulk operation
        String studentGroupName = courseRepository.getStudentGroupNameById(courseId);
        if (studentGroupName != null) {
            userService.removeGroupFromAllUsers(studentGroupName);
        }

        // Remove tutors (teaching assistants) using bulk operation
        String tutorGroupName = courseRepository.getTeachingAssistantGroupNameById(courseId);
        if (tutorGroupName != null) {
            userService.removeGroupFromAllUsers(tutorGroupName);
        }

        // Remove editors using bulk operation
        String editorGroupName = courseRepository.getEditorGroupNameById(courseId);
        if (editorGroupName != null) {
            userService.removeGroupFromAllUsers(editorGroupName);
        }
    }
}
