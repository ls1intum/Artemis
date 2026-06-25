package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseTabAccessDTO;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.lecture.api.LectureApi;
import de.tum.cit.aet.artemis.quiz.api.QuizQuestionApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

/**
 * Service for computing the lightweight per-tab access flags for the course overview.
 * <p>
 * Each flag comes from a cheap indexed {@code exists}/{@code count} query or a course column;
 * no exercises, lectures, exams, scores or participations are loaded.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class CourseTabAccessService {

    private final Optional<LectureApi> lectureApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final Optional<QuizQuestionApi> quizQuestionApi;

    private final FaqRepository faqRepository;

    public CourseTabAccessService(Optional<LectureApi> lectureApi, Optional<ExamRepositoryApi> examRepositoryApi, Optional<CourseCompetencyApi> courseCompetencyApi,
            Optional<TutorialGroupApi> tutorialGroupApi, Optional<IrisSettingsApi> irisSettingsApi, Optional<QuizQuestionApi> quizQuestionApi, FaqRepository faqRepository) {
        this.lectureApi = lectureApi;
        this.examRepositoryApi = examRepositoryApi;
        this.courseCompetencyApi = courseCompetencyApi;
        this.tutorialGroupApi = tutorialGroupApi;
        this.irisSettingsApi = irisSettingsApi;
        this.quizQuestionApi = quizQuestionApi;
        this.faqRepository = faqRepository;
    }

    /**
     * Computes the lightweight per-tab access flags for the given course and user, used by the course overview guard to decide tab access
     * without loading the full (expensive) course dashboard data. Each flag comes from a cheap indexed {@code exists}/{@code count} query or a
     * course column; no exercises, lectures, exams, scores or participations are loaded.
     *
     * @param course the course (already loaded; its columns provide the dashboard, learning-path and communication flags)
     * @param user   the user requesting access (needed for the user-scoped exam visibility check)
     * @return the per-tab access flags
     */
    public CourseTabAccessDTO getCourseTabAccess(Course course, User user) {
        long courseId = course.getId();
        boolean lecturesEnabled = lectureApi.map(api -> api.existsByCourseId(courseId)).orElse(false);
        boolean examsVisible = examRepositoryApi.map(api -> api.existsVisibleExamForUser(courseId, user.getId(), user.getGroups(), ZonedDateTime.now())).orElse(false);
        // courseHasCompetencies covers both competencies and prerequisites (single-table inheritance) in one query
        boolean competenciesOrPrerequisites = courseCompetencyApi.map(api -> api.courseHasCompetencies(courseId)).orElse(false);
        boolean tutorialGroups = tutorialGroupApi.map(api -> api.countByCourseId(courseId) > 0).orElse(false);
        boolean faqAccepted = faqRepository.countByCourseIdAndFaqState(courseId, FaqState.ACCEPTED) > 0;
        boolean irisEnabled = irisSettingsApi.map(api -> api.isIrisEnabledForCourse(courseId)).orElse(false);
        boolean trainingEnabled = quizQuestionApi.map(api -> api.areQuizExercisesInCourseAvailableForPractice(courseId)).orElse(false);
        CourseInformationSharingConfiguration config = course.getCourseInformationSharingConfiguration();
        boolean communicationEnabled = config == CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING
                || config == CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        return new CourseTabAccessDTO(lecturesEnabled, examsVisible, competenciesOrPrerequisites, tutorialGroups, course.getStudentCourseAnalyticsDashboardEnabled(), irisEnabled,
                faqAccepted, course.getLearningPathsEnabled(), communicationEnabled, trainingEnabled);
    }
}
