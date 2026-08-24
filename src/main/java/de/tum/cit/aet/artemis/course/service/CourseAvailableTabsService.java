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
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.course.dto.CourseContentAvailabilityDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.lecture.api.LectureApi;
import de.tum.cit.aet.artemis.quiz.api.QuizQuestionApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

/**
 * Service for computing which course overview tabs are available to a user.
 * <p>
 * This is the single source of truth for tab availability: the client uses it both to render the course sidebar and to
 * decide whether a tab may be opened. Each flag comes from a cheap indexed {@code exists}/{@code count} query or a
 * course column; no exercises, lectures, exams, scores or participations are loaded.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class CourseAvailableTabsService {

    private final Optional<LectureApi> lectureApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final Optional<QuizQuestionApi> quizQuestionApi;

    private final CourseRepository courseRepository;

    public CourseAvailableTabsService(Optional<LectureApi> lectureApi, Optional<ExamRepositoryApi> examRepositoryApi, Optional<CourseCompetencyApi> courseCompetencyApi,
            Optional<TutorialGroupApi> tutorialGroupApi, Optional<IrisSettingsApi> irisSettingsApi, Optional<QuizQuestionApi> quizQuestionApi, CourseRepository courseRepository) {
        this.lectureApi = lectureApi;
        this.examRepositoryApi = examRepositoryApi;
        this.courseCompetencyApi = courseCompetencyApi;
        this.tutorialGroupApi = tutorialGroupApi;
        this.irisSettingsApi = irisSettingsApi;
        this.quizQuestionApi = quizQuestionApi;
        this.courseRepository = courseRepository;
    }

    /**
     * Computes which course overview tabs are available for the given course and user. Each flag comes from a cheap
     * indexed {@code exists}/{@code count} query or a course column; no exercises, lectures, exams, scores or
     * participations are loaded.
     *
     * @param course the course (already loaded; its columns provide the learning-path and communication flags)
     * @param user   the user requesting access (needed for the user-scoped exam visibility check)
     * @return the available tabs
     */
    public CourseAvailableTabsDTO getAvailableTabs(Course course, User user) {
        long courseId = course.getId();

        // One query answers whether the course has lectures, competencies, tutorial groups, accepted FAQs and
        // practice quizzes. Each of these used to be a separate round trip, one per feature module, on every course
        // entry. The database is shared regardless of which modules are enabled, so the course side can answer them all
        // at once — but enablement still has to be applied here, otherwise a disabled module would show its tab as soon
        // as any rows happen to exist.
        CourseContentAvailabilityDTO content = courseRepository.findContentAvailability(courseId, user.getId(), FaqState.ACCEPTED, ZonedDateTime.now());
        boolean lectures = lectureApi.isPresent() && content.lectures();
        // The competency check covers both competencies and prerequisites (single-table inheritance)
        boolean competencies = courseCompetencyApi.isPresent() && content.competencies();
        boolean tutorialGroups = tutorialGroupApi.isPresent() && content.tutorialGroups();
        boolean training = quizQuestionApi.isPresent() && content.practiceQuizzes();
        boolean faq = content.acceptedFaqs();

        boolean exams = examRepositoryApi.isPresent() && content.visibleExams();

        // Iris stays a separate query: its `enabled` flag lives inside a JSON column, and when a course has no settings
        // row at all the answer comes from IrisCourseSettings.defaultSettings() in Java. Expressing that in SQL would
        // need database-specific JSON extraction and would duplicate the Java default. It is also a primary-key lookup,
        // so it is the cheapest query in this method.
        boolean iris = irisSettingsApi.map(api -> api.isIrisEnabledForCourse(courseId)).orElse(false);

        CourseInformationSharingConfiguration config = course.getCourseInformationSharingConfiguration();
        boolean communication = config == CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING || config == CourseInformationSharingConfiguration.COMMUNICATION_ONLY;
        return new CourseAvailableTabsDTO(lectures, exams, competencies, tutorialGroups, iris, faq, course.getLearningPathsEnabled(), communication, training);
    }
}
