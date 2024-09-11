package de.tum.cit.aet.artemis.service.connectors.jenkins.jobs;

import java.io.IOException;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.w3c.dom.DOMException;

@Service
@Profile("jenkins")
public class JenkinsJobPermissionsService {

    private final JenkinsJobService jenkinsJobService;

    public JenkinsJobPermissionsService(JenkinsJobService jenkinsJobService) {
        this.jenkinsJobService = jenkinsJobService;
    }

    /**
     * Assigns teaching assistant and instructor permissions to users for the specified Jenkins job.
     *
     * @param taLogins         logins of the teaching assistants
     * @param editorLogins     logins of the editors
     * @param instructorLogins logins of the instructors
     * @param folderName       the name of the Jenkins folder
     * @param jobName          the name of the Jenkins job
     * @throws IOException exception thrown when retrieving/updating the Jenkins folder failed
     */
    public void addInstructorAndEditorAndTAPermissionsToUsersForJob(Set<String> taLogins, Set<String> editorLogins, Set<String> instructorLogins, String folderName, String jobName)
            throws IOException {
        var jobConfig = jenkinsJobService.getJobConfig(folderName, jobName);
        if (jobConfig == null) {
            // Job doesn't exist so do nothing.
            return;
        }

        try {
            // Revoke previously-assigned permissions
            var allPermissions = Set.of(JenkinsJobPermission.values());
            JenkinsJobPermissionsUtils.removePermissionsFromJob(jobConfig, allPermissions, taLogins);
            JenkinsJobPermissionsUtils.removePermissionsFromJob(jobConfig, allPermissions, editorLogins);
            JenkinsJobPermissionsUtils.removePermissionsFromJob(jobConfig, allPermissions, instructorLogins);

            JenkinsJobPermissionsUtils.addPermissionsToJob(jobConfig, JenkinsJobPermission.getTeachingAssistantPermissions(), taLogins);
            JenkinsJobPermissionsUtils.addPermissionsToJob(jobConfig, JenkinsJobPermission.getEditorPermissions(), editorLogins);
            JenkinsJobPermissionsUtils.addPermissionsToJob(jobConfig, JenkinsJobPermission.getInstructorPermissions(), instructorLogins);

            jenkinsJobService.updateJob(folderName, jobName, jobConfig);

            addInstructorAndEditorAndTAPermissionsToUsersForFolder(taLogins, editorLogins, instructorLogins, folderName);
        }
        catch (DOMException e) {
            throw new IOException("Cannot add instructor, editor, and/or ta permissions to users for job: " + jobName, e);
        }
    }

    /**
     * Assigns teaching assistant and instructor permissions to users for the specified Jenkins folder.
     *
     * @param taLogins         logins of the teaching assistants
     * @param editorLogins     logins of the editors
     * @param instructorLogins logins of the instructors
     * @param folderName       the name of the Jenkins folder
     * @throws IOException exception thrown when retrieving/updating the Jenkins folder failed
     */
    public void addInstructorAndEditorAndTAPermissionsToUsersForFolder(Set<String> taLogins, Set<String> editorLogins, Set<String> instructorLogins, String folderName)
            throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);
        if (folderConfig == null) {
            // Job doesn't exist so do nothing.
            return;
        }

        try {
            // Revoke previously-assigned permissions
            JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, Set.of(JenkinsJobPermission.values()), taLogins);
            JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, Set.of(JenkinsJobPermission.values()), editorLogins);
            JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, Set.of(JenkinsJobPermission.values()), instructorLogins);

            // Assign teaching assistant permissions
            JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, JenkinsJobPermission.getTeachingAssistantPermissions(), taLogins);
            JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, JenkinsJobPermission.getEditorPermissions(), editorLogins);
            JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, JenkinsJobPermission.getInstructorPermissions(), instructorLogins);

            jenkinsJobService.updateFolderJob(folderName, folderConfig);
        }
        catch (DOMException e) {
            throw new IOException("Cannot add instructor, editor, and/or ta permissions to users for folder: " + folderName, e);
        }
    }

    /**
     * Assigns teaching assistant permissions to the user. Teaching assistants only have write access to
     * build plans.
     *
     * @param userLogin  the login of the user that will have the permissions
     * @param folderName the name of the Jenkins folder
     * @throws IOException exception thrown when retrieving/updating the Jenkins folder failed
     */
    public void addTeachingAssistantPermissionsToUserForFolder(String userLogin, String folderName) throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);
        if (folderConfig == null) {
            // Job doesn't exist so do nothing.
            return;
        }

        try {
            // Revoke previously-assigned permissions
            JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, Set.of(JenkinsJobPermission.values()), Set.of(userLogin));

            // Assign teaching assistant permissions
            JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, JenkinsJobPermission.getTeachingAssistantPermissions(), Set.of(userLogin));

            jenkinsJobService.updateFolderJob(folderName, folderConfig);
        }
        catch (DOMException e) {
            throw new IOException("Cannot add ta permissions to user for folder: " + folderName, e);
        }

    }

    /**
     * Adds all Jenkins folder permissions for the specific Jenkins user to the folder.
     * This function does not overwrite permissions that have already been given.
     *
     * @param userLogin   the login of the user that will have the permissions
     * @param folderName  the name of the Jenkins folder
     * @param permissions a list of permissions to give to the user
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void addPermissionsForUserToFolder(String userLogin, String folderName, Set<JenkinsJobPermission> permissions) throws IOException {
        addPermissionsForUsersToFolder(Set.of(userLogin), folderName, permissions);
    }

    /**
     * Adds all Jenkins folder permissions for the specific Jenkins users to the folder.
     * This function does not overwrite permissions that have already been given.
     *
     * @param userLogins  the logins of the users that will have the permissions
     * @param folderName  the name of the Jenkins folder
     * @param permissions a list of permissions to give to the users
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void addPermissionsForUsersToFolder(Set<String> userLogins, String folderName, Set<JenkinsJobPermission> permissions) throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);
        if (folderConfig == null) {
            // Job doesn't exist so do nothing.
            return;
        }

        try {
            JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, permissions, userLogins);
            jenkinsJobService.updateFolderJob(folderName, folderConfig);
        }
        catch (DOMException e) {
            throw new IOException("Cannot add permissions to users for folder: " + folderName, e);
        }
    }

    /**
     * Removes the permissions from the user for the specific Jenkins folder.
     *
     * @param userLogin           the login of the user to remove the permissions
     * @param folderName          the name of the Jenkins folder
     * @param permissionsToRemove a list of permissions to remove from the user
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void removePermissionsFromUserOfFolder(String userLogin, String folderName, Set<JenkinsJobPermission> permissionsToRemove) throws IOException {
        removePermissionsFromUsersForFolder(Set.of(userLogin), folderName, permissionsToRemove);
    }

    /**
     * Removes the permissions from the users for the specific Jenkins folder.
     *
     * @param userLogins          the logins of the users to remove the permissions
     * @param folderName          the name of the Jenkins folder
     * @param permissionsToRemove a list of permissions to remove from the users
     * @throws IOException thrown when retrieving/updating the Jenkins folder failed
     */
    public void removePermissionsFromUsersForFolder(Set<String> userLogins, String folderName, Set<JenkinsJobPermission> permissionsToRemove) throws IOException {
        var folderConfig = jenkinsJobService.getFolderConfig(folderName);
        if (folderConfig == null) {
            // Job doesn't exist so do nothing.
            return;
        }

        try {
            JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, permissionsToRemove, userLogins);
            jenkinsJobService.updateFolderJob(folderName, folderConfig);
        }
        catch (DOMException e) {
            throw new IOException("Cannot remove permissions to user for folder: " + folderName, e);

        }
    }
}
