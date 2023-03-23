package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.service.connectors.CIUserManagementService;

@Service
@Profile("gitlabci")
public class GitLabCIUserManagementService implements CIUserManagementService {

    private final Logger log = LoggerFactory.getLogger(GitLabCIUserManagementService.class);

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
