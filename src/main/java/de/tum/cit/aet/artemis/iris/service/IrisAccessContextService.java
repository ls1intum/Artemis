package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;

/**
 * Resolves the {@link PyrisAccessContextDTO} for a user: the course IDs they can access, grouped by role, so Pyris can apply them as opaque access filters during global search.
 * <p>
 * An administrator receives a present context with {@code unrestricted = true} (Pyris skips course scoping and visibility filtering for them) instead of a {@code null} context,
 * which now means "apply the safe-default visibility filter". Everyone else receives their role-grouped course IDs with {@code unrestricted = false}.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisAccessContextService {

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public IrisAccessContextService(CourseRepository courseRepository, AuthorizationCheckService authCheckService) {
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Resolves the courses the given user can access, grouped by role, into an access context for Pyris.
     *
     * @param user the requesting user (loaded with authorities, see {@code UserRepository#getUserWithAuthorities})
     * @return an access context with role-based course ID sets; an elevated administrator gets a present context with {@code unrestricted = true}
     */
    public PyrisAccessContextDTO resolveAccessContext(User user) {
        // Request-bound elevation rather than account classification: an unrestricted context lets Pyris search every
        // course, which is the global administrator override, and an administrator only holds that while their request
        // satisfies the configured passkey requirement. Every caller passes the current user, so this decides for them.
        if (authCheckService.isCurrentUserAdminAccessEnabled()) {
            // Present context with unrestricted=true (NOT null; a null context now means the safe-default filter).
            return new PyrisAccessContextDTO(List.of(), List.of(), List.of(), List.of(), List.of(), ZonedDateTime.now(), true);
        }
        var courses = courseRepository.findAllAccessibleCoursesForUser(user.getId(), false);
        var editorIds = new ArrayList<Long>();
        var taIds = new ArrayList<Long>();
        var studentIds = new ArrayList<Long>();
        for (Course course : courses) {
            if (authCheckService.isAtLeastEditorInCourse(course, user)) {
                editorIds.add(course.getId());
            }
            else if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                taIds.add(course.getId());
            }
            else {
                studentIds.add(course.getId());
            }
        }
        var staffIds = new ArrayList<Long>(editorIds.size() + taIds.size());
        staffIds.addAll(editorIds);
        staffIds.addAll(taIds);
        var allIds = courses.stream().map(Course::getId).toList();
        return new PyrisAccessContextDTO(allIds, editorIds, taIds, studentIds, staffIds, ZonedDateTime.now(), false);
    }
}
