package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.account.domain.Authority.ADMIN_AUTHORITY;
import static de.tum.cit.aet.artemis.account.domain.Authority.SUPER_ADMIN_AUTHORITY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.EDITOR;
import static de.tum.cit.aet.artemis.core.security.Role.INSTRUCTOR;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.TEACHING_ASSISTANT;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class AuthorityService {

    private final UserCourseRoleRepository userCourseRoleRepository;

    public AuthorityService(UserCourseRoleRepository userCourseRoleRepository) {
        this.userCourseRoleRepository = userCourseRoleRepository;
    }

    /**
     * Builds the Spring Security authorities for a user based on their course roles.
     * <p>
     * 1) SUPER_ADMIN / ADMIN are preserved from existing authorities.
     * 2) ROLE_INSTRUCTOR if the user holds the INSTRUCTOR role in at least one course.
     * 3) ROLE_EDITOR if the user holds the EDITOR role in at least one course.
     * 4) ROLE_TEACHING_ASSISTANT if the user holds the TEACHING_ASSISTANT role in at least one course.
     * 5) ROLE_STUDENT is always granted.
     * <p>
     * Note: these are coarse-grained global flags used for Spring Security access control
     * (e.g. accessing the management dashboard). Per-course permission is enforced separately
     * by AuthorizationCheckService using the user_course_role table.
     *
     * @param user a user whose course roles will be evaluated
     * @return a set of authorities based on the user's course roles
     */
    public Set<Authority> buildAuthorities(User user) {
        Long userId = user.getId();
        boolean isInstructor = userId != null && userCourseRoleRepository.existsByUser_IdAndRoleIn(userId, List.of(CourseRole.INSTRUCTOR));
        boolean isEditor = userId != null && userCourseRoleRepository.existsByUser_IdAndRoleIn(userId, List.of(CourseRole.EDITOR));
        boolean isTeachingAssistant = userId != null && userCourseRoleRepository.existsByUser_IdAndRoleIn(userId, List.of(CourseRole.TEACHING_ASSISTANT));
        return buildAuthorities(user, isInstructor, isEditor, isTeachingAssistant);
    }

    /**
     * Batch variant of {@link #buildAuthorities(User)} for bulk operations (e.g. registering many tutors, editors or
     * instructors at once via CSV import). Issues 3 queries in total instead of 3 per user.
     *
     * @param users the users whose authorities should be rebuilt; each must already have {@code authorities} initialized
     * @return a map from user id to the rebuilt authority set
     */
    public Map<Long, Set<Authority>> buildAuthoritiesForUsers(Collection<User> users) {
        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        Set<Long> instructorIds = userCourseRoleRepository.findUserIdsByUser_IdInAndRole(userIds, CourseRole.INSTRUCTOR);
        Set<Long> editorIds = userCourseRoleRepository.findUserIdsByUser_IdInAndRole(userIds, CourseRole.EDITOR);
        Set<Long> teachingAssistantIds = userCourseRoleRepository.findUserIdsByUser_IdInAndRole(userIds, CourseRole.TEACHING_ASSISTANT);
        return users.stream().collect(Collectors.toMap(User::getId,
                user -> buildAuthorities(user, instructorIds.contains(user.getId()), editorIds.contains(user.getId()), teachingAssistantIds.contains(user.getId()))));
    }

    private Set<Authority> buildAuthorities(User user, boolean isInstructor, boolean isEditor, boolean isTeachingAssistant) {
        Set<Authority> authorities = new HashSet<>();

        // Users who already have admin access, keep admin access.
        if (user.getAuthorities() != null && user.getAuthorities().contains(SUPER_ADMIN_AUTHORITY)) {
            authorities.add(SUPER_ADMIN_AUTHORITY);
        }
        if (user.getAuthorities() != null && user.getAuthorities().contains(ADMIN_AUTHORITY)) {
            authorities.add(ADMIN_AUTHORITY);
        }
        if (isInstructor) {
            authorities.add(new Authority(INSTRUCTOR.getAuthority()));
        }
        if (isEditor) {
            authorities.add(new Authority(EDITOR.getAuthority()));
        }
        if (isTeachingAssistant) {
            authorities.add(new Authority(TEACHING_ASSISTANT.getAuthority()));
        }
        authorities.add(new Authority(STUDENT.getAuthority()));
        return authorities;
    }
}
