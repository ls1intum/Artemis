package de.tum.cit.aet.artemis.service.connectors.gitlabci;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.service.connectors.ci.CIUserManagementService;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@Service
@Profile("gitlabci")
public class GitLabCIUserManagementService implements CIUserManagementService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIUserManagementService.class);

    @Override
    public void createUser(User user, String password) throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    @Override
    public void deleteUser(User user) throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    @Override
    public void updateUser(User user, String password) throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    @Override
    public void updateUserLogin(String oldLogin, User user, String password) throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    @Override
    public void updateUserAndGroups(String oldLogin, User user, String password, Set<String> groupsToAdd, Set<String> groupsToRemove) throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    @Override
    public void addUserToGroups(String userLogin, Set<String> group) throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    @Override
    public void removeUserFromGroups(String userLogin, Set<String> group) throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup)
            throws ContinuousIntegrationException {
        logUnsupportedAction();
    }

    private void logUnsupportedAction() {
        log.error("Please refer to the repository for user management.");
    }
}
