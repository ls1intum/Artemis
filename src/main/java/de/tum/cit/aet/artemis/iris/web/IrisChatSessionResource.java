package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisLectureChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisTextExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisLectureChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;

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

    private final IrisSessionRepository irisSessionRepository;

    protected IrisChatSessionResource(IrisCourseChatSessionRepository irisCourseChatSessionRepository, UserRepository userRepository, CourseRepository courseRepository,
            IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisCourseChatSessionService irisCourseChatSessionService, LectureRepositoryApi lectureRepositoryApi, IrisLectureChatSessionService irisLectureChatSessionService,
            IrisLectureChatSessionRepository irisLectureChatSessionRepository, IrisExerciseChatSessionRepository irisExerciseChatSessionRepository,
            ExerciseRepository exerciseRepository, IrisTextExerciseChatSessionRepository irisTextExerciseChatSessionRepository, IrisSessionRepository irisSessionRepository) {
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
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * GET chat-history/{courseId}/session/{id}: Retrieve an Iris Session for a id
     *
     * @param courseId  of the course
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the iris sessions for the id or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/session/{sessionId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisChatSession> getSessionsForSessionId(@PathVariable Long courseId, @PathVariable Long sessionId) {
        var irisSession = irisSessionRepository.findByIdWithMessagesAndContents(sessionId);

        if (irisSession == null) {
            throw new EntityNotFoundException("Iris session with id " + sessionId + " not found");
        }

        if (irisSession.shouldAcceptExternalLLMUsage()) {
            var user = userRepository.getUserWithGroupsAndAuthorities();
            user.hasAcceptedExternalLLMUsageElseThrow();
        }

        boolean enabled = switch (irisSession) {
            case IrisCourseChatSession ignored -> irisSettingsService.isEnabledForCourse(IrisSubSettingsType.COURSE_CHAT, courseId);
            case IrisLectureChatSession ignored -> irisSettingsService.isEnabledForCourse(IrisSubSettingsType.LECTURE_CHAT, courseId);
            case IrisTextExerciseChatSession ignored -> irisSettingsService.isEnabledForCourse(IrisSubSettingsType.TEXT_EXERCISE_CHAT, courseId);
            case IrisProgrammingExerciseChatSession ignored -> irisSettingsService.isEnabledForCourse(IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT, courseId);
            default -> false;
        };

        if (enabled) {
            return ResponseEntity.ok((IrisChatSession) irisSession);
        }
        throw new AccessForbiddenAlertException("This Iris chat Type is disabled in the course.", "iris", "iris.disabled");
    }

    /**
     * GET chat-history/{courseId}/sessions: Retrieve all Iris Sessions for the course
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/sessions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<IrisChatSessionDTO>> getAllSessionsForCourse(@PathVariable Long courseId) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findById(courseId).orElseThrow();
        if (user.hasAcceptedExternalLLMUsage()) {
            var irisSessionDTOs = irisSessionService.getIrisSessionsByCourseAndUserId(course, user.getId());
            return ResponseEntity.ok(irisSessionDTOs);
        }
        else {
            return ResponseEntity.ok(List.of());
        }
    }
}
