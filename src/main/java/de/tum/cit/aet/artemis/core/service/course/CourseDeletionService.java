package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.PrerequisitesApi;
import de.tum.cit.aet.artemis.communication.repository.CourseNotificationRepository;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingPresetRepository;
import de.tum.cit.aet.artemis.communication.repository.UserCourseNotificationSettingSpecificationRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.core.domain.CourseOperationType;
import de.tum.cit.aet.artemis.core.dto.CourseSummaryDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
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
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;
import de.tum.cit.aet.artemis.lecture.api.LectureApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupChannelManagementApi;

/**
 * Service for deleting a course and all its associated elements.
 * This service handles the deletion of exercises, lectures, exams, grading scales, competencies, tutorial groups, conversations, notifications,
 * and default user groups associated with the course.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseDeletionService {

    private static final Logger log = LoggerFactory.getLogger(CourseDeletionService.class);

    private static final int TOTAL_DELETE_STEPS = 15;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ExerciseRepository exerciseRepository;

    private final UserService userService;

    private final Optional<LectureApi> lectureApi;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<ExamDeletionApi> examDeletionApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final GradingScaleRepository gradingScaleRepository;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    private final Optional<PrerequisitesApi> prerequisitesApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final Optional<PyrisFaqApi> pyrisFaqApi;

    private final Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi;

    private final CourseNotificationRepository courseNotificationRepository;

    private final ConversationRepository conversationRepository;

    private final FaqRepository faqRepository;

    private final CourseRepository courseRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final CourseRequestRepository courseRequestRepository;

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final CourseOperationProgressService progressService;

    private final CourseAdminService courseAdminService;

    private final ParticipationRepository participationRepository;

    private final SubmissionRepository submissionRepository;

    public CourseDeletionService(ExerciseDeletionService exerciseDeletionService, ExerciseRepository exerciseRepository, UserService userService, Optional<LectureApi> lectureApi,
            Optional<TutorialGroupApi> tutorialGroupApi, Optional<ExamDeletionApi> examDeletionApi, Optional<ExamRepositoryApi> examRepositoryApi,
            GradingScaleRepository gradingScaleRepository, Optional<CompetencyRelationApi> competencyRelationApi, Optional<PrerequisitesApi> prerequisitesApi,
            Optional<LearnerProfileApi> learnerProfileApi, Optional<IrisSettingsApi> irisSettingsApi, Optional<PyrisFaqApi> pyrisFaqApi,
            Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi, CourseNotificationRepository courseNotificationRepository,
            ConversationRepository conversationRepository, FaqRepository faqRepository, CourseRepository courseRepository, Optional<CompetencyProgressApi> competencyProgressApi,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository, CourseRequestRepository courseRequestRepository,
            LLMTokenUsageTraceRepository llmTokenUsageTraceRepository, CourseOperationProgressService progressService, CourseAdminService courseAdminService,
            ParticipationRepository participationRepository, SubmissionRepository submissionRepository) {
        this.exerciseDeletionService = exerciseDeletionService;
        this.exerciseRepository = exerciseRepository;
        this.userService = userService;
        this.lectureApi = lectureApi;
        this.tutorialGroupApi = tutorialGroupApi;
        this.examDeletionApi = examDeletionApi;
        this.examRepositoryApi = examRepositoryApi;
        this.gradingScaleRepository = gradingScaleRepository;
        this.competencyRelationApi = competencyRelationApi;
        this.prerequisitesApi = prerequisitesApi;
        this.learnerProfileApi = learnerProfileApi;
        this.irisSettingsApi = irisSettingsApi;
        this.pyrisFaqApi = pyrisFaqApi;
        this.tutorialGroupChannelManagementApi = tutorialGroupChannelManagementApi;
        this.courseNotificationRepository = courseNotificationRepository;
        this.conversationRepository = conversationRepository;
        this.faqRepository = faqRepository;
        this.courseRepository = courseRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.courseRequestRepository = courseRequestRepository;
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.progressService = progressService;
        this.courseAdminService = courseAdminService;
        this.participationRepository = participationRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Deletes all elements associated with the course including:
     * <ul>
     * <li>The Course</li>
     * <li>All Exercises including:
     * submissions, participations, results, repositories and build plans, see {@link ExerciseDeletionService#delete}</li>
     * <li>All Lectures and their Attachments, see {@link de.tum.cit.aet.artemis.lecture.service.LectureService#delete}</li>
     * <li>All default groups created by Artemis, see {@link UserService#removeGroupFromAllUsers}</li>
     * <li>All Exams, see {@link ExamDeletionApi#deleteByCourseId(long)}</li>
     * <li>The Grading Scale if such exists, see {@link GradingScaleRepository#delete}</li>
     * <li>All Iris course settings and chat sessions</li>
     * <li>All LLM token usage traces</li>
     * <li>All learner profiles for the course</li>
     * </ul>
     * Progress is reported via WebSocket for real-time UI updates using weighted progress
     * that accounts for the complexity of each operation (e.g., programming exercises take longer).
     *
     * @param courseId the ID of the course to be deleted
     */
    public void delete(long courseId) {
        log.debug("Request to delete Course with id: {}", courseId);
        ZonedDateTime startedAt = ZonedDateTime.now();
        int stepsCompleted = 0;
        int failed = 0;

        // Calculate weighted progress based on course content
        CourseSummaryDTO summary = courseAdminService.getCourseSummary(courseId);

        // Calculate actual exam weight based on real exam data (student exams, programming exercises)
        List<ExamDeletionInfoDTO> examInfoList = examRepositoryApi.map(api -> api.findDeletionInfoByCourseId(courseId)).orElse(List.of());
        double actualExamWeight = examInfoList.stream().mapToDouble(info -> CourseOperationWeights.calculateExamWeight(info.studentExamCount(), info.programmingExerciseCount()))
                .sum();

        double totalWeight = CourseOperationWeights.calculateDeletionTotalWeight(summary, actualExamWeight);
        double completedWeight = 0;

        try {
            progressService.startOperation(courseId, CourseOperationType.DELETE, "Deleting exercises", TOTAL_DELETE_STEPS);

            // Step 1: Delete exercises (with per-exercise progress updates)
            completedWeight = deleteExercisesWithWeightedProgress(courseId, stepsCompleted, startedAt, completedWeight, totalWeight);
            stepsCompleted++;

            // Step 2: Delete lectures
            double lectureWeight = summary.numberOfLectures() * CourseOperationWeights.getWeightPerLecture();
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting lectures", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteLecturesOfCourse(courseId);
            completedWeight += lectureWeight;
            stepsCompleted++;

            // Step 3: Delete competencies
            double competencyWeight = summary.numberOfCompetencies() * CourseOperationWeights.getWeightPerCompetency()
                    + summary.numberOfCompetencyProgress() * CourseOperationWeights.getWeightPerCompetencyProgress();
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting competencies", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteCompetenciesOfCourse(courseId);
            completedWeight += competencyWeight;
            stepsCompleted++;

            // Step 4: Delete tutorial groups
            double tutorialGroupWeight = summary.numberOfTutorialGroups() * CourseOperationWeights.getWeightPerTutorialGroup();
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting tutorial groups", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteTutorialGroupsOfCourse(courseId);
            completedWeight += tutorialGroupWeight;
            stepsCompleted++;

            // Step 5: Delete conversations
            double conversationWeight = summary.numberOfConversations() * CourseOperationWeights.getWeightPerConversation()
                    + summary.numberOfPosts() * CourseOperationWeights.getWeightPerPost() + summary.numberOfAnswerPosts() * CourseOperationWeights.getWeightPerAnswer();
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting conversations", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteConversationsOfCourse(courseId);
            completedWeight += conversationWeight;
            stepsCompleted++;

            // Step 6: Delete notifications
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting notifications", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteNotificationsOfCourse(courseId);
            completedWeight += CourseOperationWeights.getWeightNotifications();
            stepsCompleted++;

            // Step 7: Delete notification presets
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting notification settings", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteNotificationsPresetsOfCourse(courseId);
            userCourseNotificationSettingSpecificationRepository.deleteAllByCourseId(courseId);
            completedWeight += CourseOperationWeights.getWeightNotificationSettings();
            stepsCompleted++;

            // Step 8: Remove users from course groups
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Removing users from groups", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            removeUsersFromCourseDefaultGroups(courseId);
            completedWeight += CourseOperationWeights.getWeightUserGroups();
            stepsCompleted++;

            // Step 9: Delete exams (with per-exam progress updates)
            completedWeight = deleteExamsWithWeightedProgress(courseId, examInfoList, stepsCompleted, startedAt, completedWeight, totalWeight);
            stepsCompleted++;

            // Step 10: Delete grading scale
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting grading scale", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteGradingScaleOfCourse(courseId);
            completedWeight += CourseOperationWeights.getWeightGradingScale();
            stepsCompleted++;

            // Step 11: Delete FAQs
            double faqWeight = summary.numberOfFaqs() * CourseOperationWeights.getWeightPerFaq();
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting FAQs", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteFaqsOfCourse(courseId);
            completedWeight += faqWeight;
            stepsCompleted++;

            // Step 12: Delete course requests
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting course requests", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteCourseRequests(courseId);
            completedWeight += CourseOperationWeights.getWeightCourseRequests();
            stepsCompleted++;

            // Step 13: Delete Iris data
            double irisWeight = summary.numberOfIrisChatSessions() * CourseOperationWeights.getWeightPerIrisSession();
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting Iris data", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteIrisData(courseId);
            completedWeight += irisWeight;
            stepsCompleted++;

            // Step 14: Delete LLM token usage traces and learner profiles
            double aiDataWeight = summary.numberOfLLMTraces() * CourseOperationWeights.getWeightPerLlmTrace()
                    + summary.numberOfLearnerProfiles() * CourseOperationWeights.getWeightPerLearnerProfile();
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting AI usage data", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            deleteLLMTokenUsageTraces(courseId);
            learnerProfileApi.ifPresent(api -> api.deleteAllForCourseId(courseId));
            completedWeight += aiDataWeight;
            stepsCompleted++;

            // Step 15: Delete the course itself
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting course", stepsCompleted, TOTAL_DELETE_STEPS, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
            courseRepository.deleteById(courseId);
            stepsCompleted++;

            progressService.completeOperation(courseId, CourseOperationType.DELETE, TOTAL_DELETE_STEPS, failed, startedAt);
            log.debug("Successfully deleted course with id {}.", courseId);
        }
        catch (Exception e) {
            log.error("Failed to delete course {}", courseId, e);
            progressService.failOperation(courseId, CourseOperationType.DELETE, "Deletion failed", stepsCompleted, TOTAL_DELETE_STEPS, failed, startedAt, e.getMessage(),
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
     * Deletes exercises with weighted progress reporting for individual exercise delete operations.
     * Each exercise's weight is calculated based on its type (programming vs other) and the number
     * of participations and submissions it has.
     *
     * @param courseId        the course ID
     * @param stepsCompleted  the number of steps completed so far
     * @param startedAt       when the operation started
     * @param completedWeight the weight of already completed operations
     * @param totalWeight     the total weight for the entire deletion
     * @return the updated completed weight after deleting all exercises
     */
    private double deleteExercisesWithWeightedProgress(long courseId, int stepsCompleted, ZonedDateTime startedAt, double completedWeight, double totalWeight) {
        Set<ExerciseDeletionInfoDTO> exercises = exerciseRepository.findDeletionInfoByCourseId(courseId);
        int totalExercises = exercises.size();
        int processed = 0;

        for (ExerciseDeletionInfoDTO exercise : exercises) {
            // Calculate weight for this specific exercise
            long participations = participationRepository.countByExerciseId(exercise.id());
            long submissions = submissionRepository.countByExerciseId(exercise.id());

            double exerciseWeight;
            if (exercise.isProgrammingExercise()) {
                exerciseWeight = CourseOperationWeights.calculateProgrammingExerciseWeight(participations, submissions);
            }
            else {
                exerciseWeight = CourseOperationWeights.calculateOtherExerciseWeight(participations, submissions);
            }

            // Report progress before deleting this exercise
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting exercise: " + exercise.title(), stepsCompleted, TOTAL_DELETE_STEPS, processed,
                    totalExercises, 0, startedAt, calculateProgressPercent(completedWeight, totalWeight));

            exerciseDeletionService.delete(exercise.id(), true);
            completedWeight += exerciseWeight;
            processed++;

            // Report progress after deleting (for responsive UI updates)
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting exercises", stepsCompleted, TOTAL_DELETE_STEPS, processed, totalExercises, 0, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
        }

        return completedWeight;
    }

    /**
     * Deletes exams with weighted progress reporting for individual exam delete operations.
     * Each exam's weight is based on its number of student exams and programming exercises.
     *
     * @param courseId        the course ID
     * @param examInfoList    pre-fetched exam deletion info (to avoid redundant queries)
     * @param stepsCompleted  the number of steps completed so far
     * @param startedAt       when the operation started
     * @param completedWeight the weight of already completed operations
     * @param totalWeight     the total weight for the entire deletion
     * @return the updated completed weight after deleting all exams
     */
    private double deleteExamsWithWeightedProgress(long courseId, List<ExamDeletionInfoDTO> examInfoList, int stepsCompleted, ZonedDateTime startedAt, double completedWeight,
            double totalWeight) {
        if (examDeletionApi.isEmpty()) {
            return completedWeight;
        }

        int totalExams = examInfoList.size();
        int processed = 0;

        for (ExamDeletionInfoDTO examInfo : examInfoList) {
            double examWeight = CourseOperationWeights.calculateExamWeight(examInfo.studentExamCount(), examInfo.programmingExerciseCount());

            // Report progress before deleting this exam
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting exam", stepsCompleted, TOTAL_DELETE_STEPS, processed, totalExams, 0, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));

            examDeletionApi.get().delete(examInfo.examId());
            completedWeight += examWeight;
            processed++;

            // Report progress after deleting
            progressService.updateProgress(courseId, CourseOperationType.DELETE, "Deleting exams", stepsCompleted, TOTAL_DELETE_STEPS, processed, totalExams, 0, startedAt,
                    calculateProgressPercent(completedWeight, totalWeight));
        }

        return completedWeight;
    }

    /**
     * Deletes all tutorial groups for the course, including their associated channels.
     * Channels must be deleted before the tutorial groups due to foreign key constraints.
     *
     * @param courseId the ID of the course whose tutorial groups should be deleted
     */
    private void deleteTutorialGroupsOfCourse(long courseId) {
        if (tutorialGroupApi.isEmpty()) {
            return;
        }
        TutorialGroupApi api = tutorialGroupApi.get();
        var tutorialGroups = api.findAllByCourseId(courseId);
        // we first need to delete notifications and channels, only then we can delete the tutorial group
        tutorialGroups.forEach(tutorialGroup -> {
            tutorialGroupChannelManagementApi.ifPresent(manApi -> manApi.deleteTutorialGroupChannel(tutorialGroup.getId()));
            api.deleteById(tutorialGroup.getId());
        });
    }

    /**
     * Deletes all conversations for the course except tutorial group channels.
     * Tutorial group channels are deleted separately in {@link #deleteTutorialGroupsOfCourse(long)}
     * because the tutorial group entity references the channel.
     * Posts and conversation participants are automatically deleted via JPA cascade.
     *
     * @param courseId the ID of the course whose conversations should be deleted
     */
    private void deleteConversationsOfCourse(long courseId) {
        // We cannot delete tutorial group channels here because the tutorial group references the channel.
        // These are deleted on deleteTutorialGroupsOfCourse().
        // Posts and Conversation Participants should be automatically deleted due to cascade
        conversationRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes the grading scale for the course if one exists.
     *
     * @param courseId the ID of the course whose grading scale should be deleted
     */
    private void deleteGradingScaleOfCourse(long courseId) {
        // delete course grading scale if it exists
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourseId(courseId);
        gradingScale.ifPresent(gradingScaleRepository::delete);
    }

    /**
     * Deletes default user groups that were created by Artemis for the course.
     * Only groups matching the default naming convention (using ARTEMIS_GROUP_DEFAULT_PREFIX)
     * are deleted. Custom groups are preserved.
     *
     * @param courseId the ID of the course whose default groups should be deleted
     */
    private void removeUsersFromCourseDefaultGroups(long courseId) {
        // only delete (default) groups which have been created by Artemis before
        String studentGroupName = courseRepository.getStudentGroupNameById(courseId);
        String defaultStudentGroupName = courseRepository.getDefaultStudentGroupNameById(courseId);
        if (Objects.equals(studentGroupName, defaultStudentGroupName)) {
            userService.removeGroupFromAllUsers(studentGroupName);
        }

        String taGroupName = courseRepository.getTeachingAssistantGroupNameById(courseId);
        String defaultTaGroupName = courseRepository.getDefaultTeachingAssistantGroupNameById(courseId);
        if (Objects.equals(taGroupName, defaultTaGroupName)) {
            userService.removeGroupFromAllUsers(taGroupName);
        }

        String editorGroupName = courseRepository.getEditorGroupNameById(courseId);
        String defaultEditorGroupName = courseRepository.getDefaultEditorGroupNameById(courseId);
        if (Objects.equals(editorGroupName, defaultEditorGroupName)) {
            userService.removeGroupFromAllUsers(editorGroupName);
        }

        String instructorGroupName = courseRepository.getInstructorGroupNameById(courseId);
        String defaultInstructorGroupName = courseRepository.getDefaultInstructorGroupNameById(courseId);
        if (Objects.equals(instructorGroupName, defaultInstructorGroupName)) {
            userService.removeGroupFromAllUsers(instructorGroupName);
        }
    }

    /**
     * Deletes all course notifications for the course.
     *
     * @param courseId the ID of the course whose notifications should be deleted
     */
    private void deleteNotificationsOfCourse(long courseId) {
        courseNotificationRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes all user notification setting presets for the course.
     *
     * @param courseId the ID of the course whose notification presets should be deleted
     */
    private void deleteNotificationsPresetsOfCourse(long courseId) {
        userCourseNotificationSettingPresetRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes all lectures for the course, including their attachments and units.
     *
     * @param courseId the ID of the course whose lectures should be deleted
     */
    private void deleteLecturesOfCourse(long courseId) {
        if (lectureApi.isEmpty()) {
            return;
        }
        var lectureIds = lectureApi.get().findLectureIdsByCourseId(courseId);
        for (Long lectureId : lectureIds) {
            lectureApi.get().deleteById(lectureId, false);
        }
    }

    /**
     * Deletes all competencies, prerequisites, and competency progress for the course.
     * This includes the competency relations and student progress data.
     *
     * @param courseId the ID of the course whose competencies should be deleted
     */
    private void deleteCompetenciesOfCourse(long courseId) {
        competencyRelationApi.ifPresent(api -> api.deleteAllByCourseId(courseId));
        prerequisitesApi.ifPresent(api -> api.deleteAllByCourseId(courseId));
        competencyProgressApi.ifPresent(api -> api.deleteAllByCourseId(courseId));
    }

    /**
     * Deletes all FAQs (Frequently Asked Questions) for the course.
     * Also notifies Pyris to remove the FAQs from the Weaviate vector database.
     *
     * @param courseId the ID of the course whose FAQs should be deleted
     */
    private void deleteFaqsOfCourse(long courseId) {
        // TODO: This implementation fetches all FAQs to notify Pyris individually, which is inefficient.
        // Pyris (https://github.com/ls1intum/edutelligence/tree/main/iris) should be enhanced to either:
        // 1. Accept a bulk deletion endpoint that only requires FAQ IDs and course ID, or
        // 2. Provide a single REST endpoint to delete all ingested data for a course at once (preferred)
        // See: https://github.com/ls1intum/edutelligence/blob/main/iris/src/iris/pipeline/faq_ingestion_pipeline.py
        var faqs = faqRepository.findAllByCourseId(courseId);
        pyrisFaqApi.ifPresent(api -> faqs.forEach(api::deleteFaq));
        faqRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes all course creation requests that resulted in this course.
     *
     * @param courseId the ID of the course whose creation requests should be deleted
     */
    private void deleteCourseRequests(long courseId) {
        courseRequestRepository.deleteAllByCreatedCourseId(courseId);
    }

    /**
     * Deletes all Iris AI settings and chat sessions for the course.
     * This includes the course-specific Iris configuration and all student conversations with the AI tutor.
     *
     * @param courseId the ID of the course whose Iris data should be deleted
     */
    private void deleteIrisData(long courseId) {
        irisSettingsApi.ifPresent(api -> {
            api.deleteCourseSettings(courseId);
            api.deleteCourseChatSessions(courseId);
        });
    }

    /**
     * Deletes all LLM token usage traces for the course.
     * This removes records tracking AI model usage (tokens, costs) for this course.
     *
     * @param courseId the ID of the course whose LLM traces should be deleted
     */
    private void deleteLLMTokenUsageTraces(long courseId) {
        llmTokenUsageTraceRepository.deleteAllByCourseId(courseId);
    }
}
