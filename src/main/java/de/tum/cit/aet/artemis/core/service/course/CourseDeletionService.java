package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.core.service.user.UserService;
import de.tum.cit.aet.artemis.exam.api.ExamDeletionApi;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.lecture.api.LectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupChannelManagementApi;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupApiNotPresentException;

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

    private final ExerciseDeletionService exerciseDeletionService;

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

    private final Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi;

    private final CourseNotificationRepository courseNotificationRepository;

    private final ConversationRepository conversationRepository;

    private final FaqRepository faqRepository;

    private final CourseRepository courseRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository;

    private final UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository;

    private final CourseRequestRepository courseRequestRepository;

    public CourseDeletionService(ExerciseDeletionService exerciseDeletionService, UserService userService, Optional<LectureApi> lectureApi,
            Optional<TutorialGroupApi> tutorialGroupApi, Optional<ExamDeletionApi> examDeletionApi, Optional<ExamRepositoryApi> examRepositoryApi,
            GradingScaleRepository gradingScaleRepository, Optional<CompetencyRelationApi> competencyRelationApi, Optional<PrerequisitesApi> prerequisitesApi,
            Optional<LearnerProfileApi> learnerProfileApi, Optional<IrisSettingsApi> irisSettingsApi, Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi,
            CourseNotificationRepository courseNotificationRepository, ConversationRepository conversationRepository, FaqRepository faqRepository,
            CourseRepository courseRepository, Optional<CompetencyProgressApi> competencyProgressApi,
            UserCourseNotificationSettingPresetRepository userCourseNotificationSettingPresetRepository,
            UserCourseNotificationSettingSpecificationRepository userCourseNotificationSettingSpecificationRepository, CourseRequestRepository courseRequestRepository) {
        this.exerciseDeletionService = exerciseDeletionService;
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
        this.tutorialGroupChannelManagementApi = tutorialGroupChannelManagementApi;
        this.courseNotificationRepository = courseNotificationRepository;
        this.conversationRepository = conversationRepository;
        this.faqRepository = faqRepository;
        this.courseRepository = courseRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.userCourseNotificationSettingPresetRepository = userCourseNotificationSettingPresetRepository;
        this.userCourseNotificationSettingSpecificationRepository = userCourseNotificationSettingSpecificationRepository;
        this.courseRequestRepository = courseRequestRepository;
    }

    /**
     * Deletes all elements associated with the course including:
     * <ul>
     * <li>The Course</li>
     * <li>All Exercises including:
     * submissions, participations, results, repositories and build plans, see {@link ExerciseDeletionService#delete}</li>
     * <li>All Lectures and their Attachments, see {@link de.tum.cit.aet.artemis.lecture.service.LectureService#delete}</li>
     * <li>All default groups created by Artemis, see {@link UserService#deleteGroup}</li>
     * <li>All Exams, see {@link ExamDeletionApi#deleteByCourseId(long)}</li>
     * <li>The Grading Scale if such exists, see {@link GradingScaleRepository#delete}</li>
     * </ul>
     *
     * @param course the course to be deleted
     */
    public void delete(Course course) {
        log.debug("Request to delete Course : {}", course.getTitle());
        long courseId = course.getId();

        // TODO: we should try to delete objects based on their id without fetching them completely first
        deleteExercisesOfCourse(course);
        deleteLecturesOfCourse(course);
        deleteCompetenciesOfCourse(course);
        deleteTutorialGroupsOfCourse(course);
        deleteConversationsOfCourse(course);
        deleteNotificationsOfCourse(courseId);
        deleteNotificationsPresetsOfCourse(courseId);
        userCourseNotificationSettingSpecificationRepository.deleteAllByCourseId(courseId);
        deleteDefaultGroups(course);
        deleteExamsOfCourse(courseId);
        deleteGradingScaleOfCourse(course);
        deleteFaqsOfCourse(course);
        deleteCourseRequests(course.getId());
        learnerProfileApi.ifPresent(api -> api.deleteAllForCourse(course));
        irisSettingsApi.ifPresent(api -> api.deleteSettingsFor(course));
        courseRepository.deleteById(course.getId());
        log.debug("Successfully deleted course {}.", course.getTitle());
    }

    private void deleteTutorialGroupsOfCourse(Course course) {
        TutorialGroupApi api = tutorialGroupApi.orElseThrow(() -> new TutorialGroupApiNotPresentException(TutorialGroupApi.class));
        var tutorialGroups = api.findAllByCourseId(course.getId());
        // we first need to delete notifications and channels, only then we can delete the tutorial group
        tutorialGroups.forEach(tutorialGroup -> {
            tutorialGroupChannelManagementApi.ifPresent(manApi -> manApi.deleteTutorialGroupChannel(tutorialGroup));
            api.deleteById(tutorialGroup.getId());
        });
    }

    private void deleteConversationsOfCourse(Course course) {
        // We cannot delete tutorial group channels here because the tutorial group references the channel.
        // These are deleted on deleteTutorialGroupsOfCourse().
        // Posts and Conversation Participants should be automatically deleted due to cascade
        conversationRepository.deleteAllByCourseId(course.getId());
    }

    private void deleteGradingScaleOfCourse(Course course) {
        // delete course grading scale if it exists
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourseId(course.getId());
        gradingScale.ifPresent(gradingScaleRepository::delete);
    }

    private void deleteExamsOfCourse(long courseId) {
        if (examDeletionApi.isEmpty() || examRepositoryApi.isEmpty()) {
            return;
        }
        this.examDeletionApi.get().deleteByCourseId(courseId);
    }

    private void deleteDefaultGroups(Course course) {
        // only delete (default) groups which have been created by Artemis before
        if (Objects.equals(course.getStudentGroupName(), course.getDefaultStudentGroupName())) {
            userService.deleteGroup(course.getStudentGroupName());
        }
        if (Objects.equals(course.getTeachingAssistantGroupName(), course.getDefaultTeachingAssistantGroupName())) {
            userService.deleteGroup(course.getTeachingAssistantGroupName());
        }
        if (Objects.equals(course.getEditorGroupName(), course.getDefaultEditorGroupName())) {
            userService.deleteGroup(course.getEditorGroupName());
        }
        if (Objects.equals(course.getInstructorGroupName(), course.getDefaultInstructorGroupName())) {
            userService.deleteGroup(course.getInstructorGroupName());
        }
    }

    private void deleteNotificationsOfCourse(long courseId) {
        courseNotificationRepository.deleteAllByCourseId(courseId);
    }

    private void deleteNotificationsPresetsOfCourse(long courseId) {
        userCourseNotificationSettingPresetRepository.deleteAllByCourseId(courseId);
    }

    private void deleteLecturesOfCourse(Course course) {
        if (lectureApi.isEmpty()) {
            return;
        }
        LectureApi api = lectureApi.get();

        for (Lecture lecture : course.getLectures()) {
            api.delete(lecture, false);
        }
    }

    private void deleteExercisesOfCourse(Course course) {
        for (Exercise exercise : course.getExercises()) {
            exerciseDeletionService.delete(exercise.getId(), true);
        }
    }

    private void deleteCompetenciesOfCourse(Course course) {
        competencyRelationApi.ifPresent(api -> api.deleteAllByCourseId(course.getId()));
        prerequisitesApi.ifPresent(api -> api.deleteAllByCourseId(course.getId()));
        competencyProgressApi.ifPresent(api -> api.deleteAllByCourseId(course.getId()));
    }

    private void deleteFaqsOfCourse(Course course) {
        faqRepository.deleteAllByCourseId(course.getId());
    }

    private void deleteCourseRequests(Long courseId) {
        courseRequestRepository.deleteAllByCreatedCourseId(courseId);
    }
}
