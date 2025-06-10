package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IrisSessionDTO;
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
     * GET course-chat/{courseId}/sessions: Retrieve all Iris Sessions for the course
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/sessions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<IrisSessionDTO>> getAllSessionsForCourse(@PathVariable Long courseId) {
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

    private List<IrisSessionDTO> getAllSessionsForCourseChat(Long courseId) {
        var course = courseRepository.findById(courseId);
        if (course.isPresent()) {
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.COURSE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();

                var sessions = irisCourseChatSessionRepository.findByCourseIdAndUserId(course.get().getId(), user.getId());
                sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
                return sessions.stream()
                        .map(s -> new IrisSessionDTO(s.getId(), s.getUserId(), s.getMessages(), s.getCreationDate(), IrisSubSettingsType.COURSE_CHAT, s.getCourseId())).toList();
            }
        }
        return null;
    }

    private List<IrisSessionDTO> getAllSessionsForLectureChat(Long courseId) {
        var course = courseRepository.findById(courseId);

        if (course.isPresent()) {
            Set<Lecture> lecturesForCourse = lectureRepositoryApi.findAllByCourseId(courseId);

            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.LECTURE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();
                List<IrisLectureChatSession> sessions = lecturesForCourse.stream()
                        .flatMap(l -> irisLectureChatSessionRepository.findByLectureIdAndUserIdOrderByCreationDateDesc(l.getId(), user.getId()).stream()).toList();
                sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
                return sessions.stream()
                        .map(s -> new IrisSessionDTO(s.getId(), s.getUserId(), s.getMessages(), s.getCreationDate(), IrisSubSettingsType.LECTURE_CHAT, s.getLectureId())).toList();
            }
        }
        return null;
    }

    private List<IrisSessionDTO> getAllSessionsForProgrammingExerciseChat(Long courseId) {
        var course = courseRepository.findById(courseId);
        if (course.isPresent()) {
            var exercisesForCourse = exerciseRepository.findAllExercisesByCourseId(courseId);
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();

                var sessions = exercisesForCourse.stream().flatMap(e -> irisExerciseChatSessionRepository.findByExerciseIdAndUserId(e.getId(), user.getId()).stream()).toList();
                sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
                return sessions.stream().map(
                        s -> new IrisSessionDTO(s.getId(), s.getUserId(), s.getMessages(), s.getCreationDate(), IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, s.getExerciseId()))
                        .toList();
            }
        }
        return null;
    }

    private List<IrisSessionDTO> getAllSessionsForTextExerciseChat(Long courseId) {
        var course = courseRepository.findById(courseId);
        if (course.isPresent()) {
            var exercisesForCourse = exerciseRepository.findAllExercisesByCourseId(courseId);
            if (irisSettingsService.isEnabledFor(IrisSubSettingsType.TEXT_EXERCISE_CHAT, course.get())) {
                var user = userRepository.getUserWithGroupsAndAuthorities();

                var sessions = exercisesForCourse.stream().flatMap(e -> irisTextExerciseChatSessionRepository.findByExerciseIdAndUserId(e.getId(), user.getId()).stream()).toList();
                sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
                return sessions.stream()
                        .map(s -> new IrisSessionDTO(s.getId(), s.getUserId(), s.getMessages(), s.getCreationDate(), IrisSubSettingsType.TEXT_EXERCISE_CHAT, s.getExerciseId()))
                        .toList();
            }
        }
        return null;
    }

}
