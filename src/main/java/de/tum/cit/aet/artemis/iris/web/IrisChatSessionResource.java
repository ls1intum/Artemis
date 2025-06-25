package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisLectureChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisTextExerciseChatSessionRepository;
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
@Lazy
@RestController
@RequestMapping("api/iris/chat-history/")
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

    private final ExerciseRepository exerciseRepository;

    private final IrisLectureChatSessionService irisLectureChatSessionService;

    private final IrisLectureChatSessionRepository irisLectureChatSessionRepository;

    private final IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    private final IrisTextExerciseChatSessionRepository irisTextExerciseChatSessionRepository;

    protected IrisChatSessionResource(IrisCourseChatSessionRepository irisCourseChatSessionRepository, UserRepository userRepository, CourseRepository courseRepository,
            IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisCourseChatSessionService irisCourseChatSessionService, LectureRepositoryApi lectureRepositoryApi, IrisLectureChatSessionService irisLectureChatSessionService,
            IrisLectureChatSessionRepository irisLectureChatSessionRepository, IrisExerciseChatSessionRepository irisExerciseChatSessionRepository,
            ExerciseRepository exerciseRepository, IrisTextExerciseChatSessionRepository irisTextExerciseChatSessionRepository) {
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
        this.exerciseRepository = exerciseRepository;
        this.irisTextExerciseChatSessionRepository = irisTextExerciseChatSessionRepository;
    }

    /**
     * GET chat-history/{courseId}/session/{sessionId}: Retrieve an Iris Session for a sessionId
     *
     * @param courseId  of the course
     * @param chatMode  of the session
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the iris sessions for the sessionId or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/{chatMode}/session/{sessionId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisChatSession> getSessionsForSessionId(@PathVariable Long courseId, @PathVariable Long sessionId, @PathVariable String chatMode) {
        var chatModeEnum = IrisChatMode.fromValue(chatMode);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        user.hasAcceptedExternalLLMUsageElseThrow();

        IrisChatSession session = null;

        if (chatModeEnum.equals(IrisChatMode.COURSE)) {
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.COURSE_CHAT, course)) {
                IrisCourseChatSession courseChatSession = irisCourseChatSessionRepository.findSessionWithMessagesById(sessionId).orElseThrow();
                courseChatSession.setEntityId(courseChatSession.getCourseId());
                session = courseChatSession;
            }
        }
        else if (chatModeEnum.equals(IrisChatMode.LECTURE)) {
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.LECTURE_CHAT, course)) {
                IrisLectureChatSession lectureChatSession = irisLectureChatSessionRepository.findSessionWithMessagesById(sessionId).orElseThrow();
                lectureChatSession.setEntityId(lectureChatSession.getLectureId());
                session = lectureChatSession;
            }
        }
        else if (chatModeEnum.equals(IrisChatMode.TEXT_EXERCISE)) {
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.TEXT_EXERCISE_CHAT, course)) {
                IrisTextExerciseChatSession textExerciseChatSession = irisTextExerciseChatSessionRepository.findSessionWithMessagesById(sessionId).orElseThrow();
                textExerciseChatSession.setEntityId(textExerciseChatSession.getExerciseId());
                session = textExerciseChatSession;
            }
        }
        else if (chatModeEnum.equals(IrisChatMode.PROGRAMMING_EXERCISE)) {
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, course)) {
                IrisProgrammingExerciseChatSession programmingExerciseChatSession = irisExerciseChatSessionRepository.findSessionWithMessagesById(sessionId).orElseThrow();
                programmingExerciseChatSession.setEntityId(programmingExerciseChatSession.getExerciseId());
                session = programmingExerciseChatSession;
            }
        }

        return ResponseEntity.ok(session);
    }

    /**
     * GET chat-history/{courseId}/sessions: Retrieve all Iris Sessions for the course
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/sessions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<IrisChatSession>> getAllSessionsForCourse(@PathVariable Long courseId) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        if (user.hasAcceptedExternalLLMUsage()) {
            var allChatSessions = Stream.of(getAllSessionsForCourseChat(courseId), getAllSessionsForLectureChat(courseId), getAllSessionsForProgrammingExerciseChat(courseId),
                    getAllSessionsForTextExerciseChat(courseId)).filter(Objects::nonNull).flatMap(List::stream).toList();
            return ResponseEntity.ok(allChatSessions);
        }
        else {
            return ResponseEntity.ok(List.of());
        }
    }

    private List<IrisChatSession> getAllSessionsForCourseChat(Long courseId) {
        var course = courseRepository.findById(courseId);
        if (course.isPresent()) {
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.COURSE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();

                var sessions = irisCourseChatSessionRepository.findLatestByCourseIdAndUserIdWithMessages(course.get().getId(), user.getId(), Pageable.unpaged());
                sessions.forEach(s -> {
                    irisSessionService.checkHasAccessToIrisSession(s, user);
                    s.setEntityId(s.getCourseId());
                });
                return new ArrayList<>(sessions);
            }
        }
        return Collections.emptyList();
    }

    private List<IrisChatSession> getAllSessionsForLectureChat(Long courseId) {
        var course = courseRepository.findById(courseId);

        if (course.isPresent()) {
            Set<Lecture> lecturesForCourse = lectureRepositoryApi.findAllByCourseId(courseId);

            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.LECTURE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();
                List<IrisLectureChatSession> sessions = lecturesForCourse.stream()
                        .flatMap(l -> irisLectureChatSessionRepository.findLatestSessionsByLectureIdAndUserIdWithMessages(l.getId(), user.getId(), Pageable.unpaged()).stream())
                        .toList();
                sessions.forEach(s -> {
                    irisSessionService.checkHasAccessToIrisSession(s, user);
                    s.setEntityId(s.getLectureId());
                });
                return new ArrayList<>(sessions);
            }
        }
        return null;
    }

    private List<IrisChatSession> getAllSessionsForProgrammingExerciseChat(Long courseId) {
        var course = courseRepository.findById(courseId);
        if (course.isPresent()) {
            var exercisesForCourse = exerciseRepository.findAllExercisesByCourseId(courseId);
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();

                var sessions = exercisesForCourse.stream()
                        .flatMap(e -> irisExerciseChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(e.getId(), user.getId(), Pageable.unpaged()).stream()).toList();
                sessions.forEach(s -> {
                    irisSessionService.checkHasAccessToIrisSession(s, user);
                    s.setEntityId(s.getExerciseId());
                });
                return new ArrayList<>(sessions);
            }
        }
        return null;
    }

    private List<IrisChatSession> getAllSessionsForTextExerciseChat(Long courseId) {
        var course = courseRepository.findById(courseId);
        if (course.isPresent()) {
            var exercisesForCourse = exerciseRepository.findAllExercisesByCourseId(courseId);
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.TEXT_EXERCISE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();

                var sessions = exercisesForCourse.stream()
                        .flatMap(e -> irisTextExerciseChatSessionRepository.findLatestByExerciseIdAndUserIdWithMessages(e.getId(), user.getId(), Pageable.unpaged()).stream())
                        .toList();
                sessions.forEach(s -> {
                    irisSessionService.checkHasAccessToIrisSession(s, user);
                    s.setEntityId(s.getExerciseId());
                });
                return new ArrayList<>(sessions);
            }
        }
        return null;
    }

}
