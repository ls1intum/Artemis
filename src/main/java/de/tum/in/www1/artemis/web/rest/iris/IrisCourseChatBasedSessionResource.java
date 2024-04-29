package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisHealthStatusDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSubSettingsInterface;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;

/**
 * REST controller base class for managing exercise chat based {@link IrisSession}s.
 */
public abstract class IrisCourseChatBasedSessionResource<C extends Course, S extends IrisSession> {

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final CourseRepository courseRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected IrisCourseChatBasedSessionResource(AuthorizationCheckService authCheckService, UserRepository userRepository, IrisSessionService irisSessionService,
            IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService, CourseRepository courseRepository) {
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
    }

    protected ResponseEntity<S> getCurrentSession(Long courseId, IrisSubSettingsType subSettingsType, Role role, BiFunction<Course, User, S> sessionsFunction) {
        var course = courseRepository.findByIdElseThrow(courseId);
        irisSettingsService.isEnabledForElseThrow(subSettingsType, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, user);

        var session = sessionsFunction.apply(course, user);
        irisSessionService.checkHasAccessToIrisSession(session, user);
        return ResponseEntity.ok(session);
    }

    protected ResponseEntity<List<S>> getAllSessions(Long courseId, IrisSubSettingsType subSettingsType, Role role, BiFunction<Course, User, List<S>> sessionsFunction) {
        var course = courseRepository.findByIdElseThrow(courseId);
        irisSettingsService.isEnabledForElseThrow(subSettingsType, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, user);

        var sessions = sessionsFunction.apply(course, user);
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    protected ResponseEntity<S> createSessionForCourse(Long courseId, IrisSubSettingsType subSettingsType, Role role, BiFunction<Course, User, S> sessionsFunction)
            throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);
        irisSettingsService.isEnabledForElseThrow(subSettingsType, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, user);

        var session = sessionsFunction.apply(course, user);

        var uriString = "/api/iris/sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    protected IrisHealthDTO isIrisActiveInternal(C course, S session, Function<IrisCombinedSettingsDTO, IrisCombinedSubSettingsInterface> subSettingsFunction) {
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        irisSessionService.checkIsIrisActivated(session);
        var settings = irisSettingsService.getCombinedIrisSettingsFor(course, false);
        var health = pyrisHealthIndicator.health();
        PyrisHealthStatusDTO[] modelStatuses = (PyrisHealthStatusDTO[]) health.getDetails().get("modelStatuses");
        var specificModelStatus = false;
        if (modelStatuses != null) {
            specificModelStatus = Arrays.stream(modelStatuses).filter(x -> x.model().equals(subSettingsFunction.apply(settings).getPreferredModel()))
                    .anyMatch(x -> x.status() == PyrisHealthStatusDTO.ModelStatus.UP);
        }

        IrisRateLimitService.IrisRateLimitInformation rateLimitInfo = null;

        // TODO: Refactor rate limit service in the future
        if (session instanceof IrisCourseChatSession) {
            rateLimitInfo = irisRateLimitService.getRateLimitInformation(user);
        }

        return new IrisHealthDTO(health.getStatus() == Status.UP, rateLimitInfo);
    }

    public record IrisHealthDTO(boolean active, IrisRateLimitService.IrisRateLimitInformation rateLimitInfo) {
    }
}
