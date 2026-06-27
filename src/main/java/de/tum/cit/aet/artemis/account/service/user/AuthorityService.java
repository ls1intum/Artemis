package de.tum.cit.aet.artemis.account.service.user;

import static de.tum.cit.aet.artemis.account.domain.Authority.ADMIN_AUTHORITY;
import static de.tum.cit.aet.artemis.account.domain.Authority.SUPER_ADMIN_AUTHORITY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.EDITOR;
import static de.tum.cit.aet.artemis.core.security.Role.INSTRUCTOR;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.TEACHING_ASSISTANT;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Set<Authority> authorities = new HashSet<>();

        // Users who already have admin access, keep admin access.
        if (user.getAuthorities() != null && user.getAuthorities().contains(SUPER_ADMIN_AUTHORITY)) {
            authorities.add(SUPER_ADMIN_AUTHORITY);
        }
        if (user.getAuthorities() != null && user.getAuthorities().contains(ADMIN_AUTHORITY)) {
            authorities.add(ADMIN_AUTHORITY);
        }

        Long userId = user.getId();
        if (userId != null) {
            // Check if user is an instructor in any course
            if (userCourseRoleRepository.existsByUser_IdAndRoleIn(userId, List.of(CourseRole.INSTRUCTOR))) {
                authorities.add(new Authority(INSTRUCTOR.getAuthority()));
            }

            // Check if user is an editor in any course
            if (userCourseRoleRepository.existsByUser_IdAndRoleIn(userId, List.of(CourseRole.EDITOR))) {
                authorities.add(new Authority(EDITOR.getAuthority()));
            }

            // Check if user is a tutor in any course
            if (userCourseRoleRepository.existsByUser_IdAndRoleIn(userId, List.of(CourseRole.TEACHING_ASSISTANT))) {
                authorities.add(new Authority(TEACHING_ASSISTANT.getAuthority()));
            }
        }

        authorities.add(new Authority(STUDENT.getAuthority()));
        return authorities;
    }
}
