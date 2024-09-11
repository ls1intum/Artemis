package de.tum.cit.aet.artemis.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.Role.EDITOR;
import static de.tum.cit.aet.artemis.core.security.Role.INSTRUCTOR;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.TEACHING_ASSISTANT;
import static de.tum.cit.aet.artemis.domain.Authority.ADMIN_AUTHORITY;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Authority;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.CourseRepository;

@Profile(PROFILE_CORE)
@Service
public class AuthorityService {

    @Value("${artemis.user-management.external.admin-group-name:#{null}}")
    private Optional<String> adminGroupName;

    private final CourseRepository courseRepository;

    public AuthorityService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /**
     * Builds the authorities list from the groups:
     * <p>
     * 1) Admin group if the globally defined ADMIN_GROUP_NAME is available and is contained in the users groups, or if the user was an admin before
     * 2) group contains configured instructor group name -> instructor role
     * 3) group contains configured tutor group name -> tutor role
     * 4) the user role is always given
     *
     * @param user a user with groups
     * @return a set of authorities based on the course configuration and the given groups
     */
    public Set<Authority> buildAuthorities(User user) {
        Set<Authority> authorities = new HashSet<>();
        Set<String> groups = user.getGroups();
        if (groups == null) {
            // prevent null pointer exceptions
            groups = new HashSet<>();
        }

        // Check if the user is admin in case the admin group is defined
        if (adminGroupName.isPresent() && groups.contains(adminGroupName.get())) {
            authorities.add(ADMIN_AUTHORITY);
        }

        // Users who already have admin access, keep admin access.
        if (user.getAuthorities() != null && user.getAuthorities().contains(ADMIN_AUTHORITY)) {
            authorities.add(ADMIN_AUTHORITY);
        }

        Set<String> instructorGroups = courseRepository.findAllInstructorGroupNames();
        Set<String> editorGroups = courseRepository.findAllEditorGroupNames();
        Set<String> teachingAssistantGroups = courseRepository.findAllTeachingAssistantGroupNames();

        // Check if user is an instructor in any course
        if (groups.stream().anyMatch(instructorGroups::contains)) {
            authorities.add(new Authority(INSTRUCTOR.getAuthority()));
        }

        // Check if user is an editor in any course
        if (groups.stream().anyMatch(editorGroups::contains)) {
            authorities.add(new Authority(EDITOR.getAuthority()));
        }

        // Check if user is a tutor in any course
        if (groups.stream().anyMatch(teachingAssistantGroups::contains)) {
            authorities.add(new Authority(TEACHING_ASSISTANT.getAuthority()));
        }

        authorities.add(new Authority(STUDENT.getAuthority()));
        return authorities;
    }
}
