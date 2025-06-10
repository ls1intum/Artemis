package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisLectureChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * REST controller for managing {@link IrisChatSession}.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/iris/chat/")
public class IrisChatSessionResource {

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final CourseRepository courseRepository;

    private final IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    private final LectureRepositoryApi lectureRepositoryApi;

    private final IrisLectureChatSessionService irisLectureChatSessionService;

    private final IrisLectureChatSessionRepository irisLectureChatSessionRepository;

    private final IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    protected IrisChatSessionResource(IrisCourseChatSessionRepository irisCourseChatSessionRepository, UserRepository userRepository, CourseRepository courseRepository,
            IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisCourseChatSessionService irisCourseChatSessionService, LectureRepositoryApi lectureRepositoryApi, IrisLectureChatSessionService irisLectureChatSessionService,
            IrisLectureChatSessionRepository irisLectureChatSessionRepository, IrisExerciseChatSessionRepository irisExerciseChatSessionRepository) {
        this.irisCourseChatSessionRepository = irisCourseChatSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.courseRepository = courseRepository;
        this.irisCourseChatSessionService = irisCourseChatSessionService;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.irisLectureChatSessionService = irisLectureChatSessionService;
        this.irisLectureChatSessionRepository = irisLectureChatSessionRepository;
        this.irisExerciseChatSessionRepository = irisExerciseChatSessionRepository;
    }

    /**
     * GET course-chat/{courseId}/sessions: Retrieve all Iris Sessions for the course
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/sessions")
    @EnforceAtLeastStudentInCourse
    public List<IrisChatSession> getAllSessionsForCourse(Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.COURSE_CHAT, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasAcceptedExternalLLMUsageElseThrow();

        var sessions = irisCourseChatSessionRepository.findByExerciseIdAndUserIdElseThrow(course.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));

        Set<Lecture> lecturesForCourse = lectureRepositoryApi.findAllByCourseId(courseId);

        return ResponseEntity.ok(sessions);
    }

    private List<IrisCourseChatSession> getAllSessionsForCourseChat(Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.COURSE_CHAT, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasAcceptedExternalLLMUsageElseThrow();

        var sessions = irisCourseChatSessionRepository.findByExerciseIdAndUserIdElseThrow(course.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return sessions;
    }

    private List<IrisLectureChatSession> getAllSessionsForLectureChat(Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);

        Set<Lecture> lecturesForCourse = lectureRepositoryApi.findAllByCourseId(courseId);

        if (irisSettingsService.isEnabledFor(IrisSubSettingsType.LECTURE_CHAT, course)) {
            var user = userRepository.getUserWithGroupsAndAuthorities();
            user.hasAcceptedExternalLLMUsageElseThrow();
            List<IrisLectureChatSession> sessions = lecturesForCourse.stream()
                    .map(l -> irisLectureChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(l.getId(), user.getId())).collect(Collectors.toList());
            sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
            return sessions;
        }
        return null;
    }

    private List<IrisChatSession> getAllSessionsForExerciseChat(Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);

        if (irisSettingsService.isEnabledFor(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, course)) {
            var user = userRepository.getUserWithGroupsAndAuthorities();
            user.hasAcceptedExternalLLMUsageElseThrow();

            // var sessions = irisExerciseChatSessionRepository.find;
            sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
            return sessions;
        }
        return null;
    }
}
