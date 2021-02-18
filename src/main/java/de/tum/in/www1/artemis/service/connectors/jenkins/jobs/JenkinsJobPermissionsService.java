package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

import java.io.IOException;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.offbytwo.jenkins.JenkinsServer;

@Service
@Profile("jenkins")
public class JenkinsJobPermissionsService {

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsServer jenkinsServer;

    public JenkinsJobPermissionsService(JenkinsServer jenkinsServer) {
        this.jenkinsServer = jenkinsServer;
    }

    /**
     * Assigns instructor permissions to the user. Instructors have admin access to build plans.
     *
     * @param userLogin the login of the user that will have the permissions
     * @param jobName the name of the job where the permissions will take affect
     * @throws IOException exception thrown when retrieving/updating the Jenkins job failed
     */
    public void addInstructorPermissionsToUserForJob(String userLogin, String folderName, String jobName) throws IOException {
        addPermissionsForUserToJob(userLogin, folderName, jobName, JenkinsJobPermission.getInstructorPermissions());
    }

    /**
     * Assigns teaching assistant and instructor permissions to users.
     *
     * @param taLogins logins of the teaching assisstants
     * @param instructorLogins logins of the instructors
     * @param jobName the name of the job where the permissions will take affect
     * @throws IOException exception thrown when retrieving/updating the Jenkins job failed
     */
    public void addInstructorAndTAPermissionsToUsersForJob(Set<String> taLogins, Set<String> instructorLogins, String folderName, String jobName) throws IOException {
        var jobConfigDocument = getJobConfigXmlDocument(folderName, jobName);

        // Revoke previously-assigned permissions
        JenkinsJobPermissionsUtils.removePermissionsFromDocument(jobConfigDocument, Set.of(JenkinsJobPermission.values()), taLogins);
        JenkinsJobPermissionsUtils.removePermissionsFromDocument(jobConfigDocument, Set.of(JenkinsJobPermission.values()), instructorLogins);

        // Assign teaching assistant permissions
        JenkinsJobPermissionsUtils.addPermissionsToDocument(jobConfigDocument, JenkinsJobPermission.getTeachingAssistantPermissions(), taLogins);
        JenkinsJobPermissionsUtils.addPermissionsToDocument(jobConfigDocument, JenkinsJobPermission.getInstructorPermissions(), instructorLogins);

        jenkinsServer.updateJob(jobName, jobConfigDocument.toString(), useCrumb);
    }

    /**
     * Assigns teaching assistant permissions to the user. Teaching assistants only have write access to
     * build plans.
     *
     * @param userLogin the login of the user that will have the permissions
     * @param jobName the name of the job where the permissions will take affect
     * @throws IOException exception thrown when retrieving/updating the Jenkins job failed
     */
    public void addTeachingAssistantPermissionsToUserForJob(String userLogin, String folderName, String jobName) throws IOException {
        var jobConfigDocument = getJobConfigXmlDocument(folderName, jobName);

        // Revoke previously-assigned permissions
        JenkinsJobPermissionsUtils.removePermissionsFromDocument(jobConfigDocument, Set.of(JenkinsJobPermission.values()), userLogin);

        // Assign teaching assistant permissions
        JenkinsJobPermissionsUtils.addPermissionsToDocument(jobConfigDocument, JenkinsJobPermission.getTeachingAssistantPermissions(), Set.of(userLogin));

        jenkinsServer.updateJob(jobName, jobConfigDocument.toString(), useCrumb);
    }

    /**
     * Adds all Jenkins job permissions for the specific Jenkins user to the job. This function does not overwrite
     * permissions that have already been given.
     *
     * @param userLogin the login of the user that will have the permissions
     * @param jobName the name of the job where the permissions will take affect
     * @param permissions a list of permissions to give to the user
     * @throws IOException thrown when retrieving/updating the Jenkins job failed
     */
    public void addPermissionsForUserToJob(String userLogin, String folderName, String jobName, Set<JenkinsJobPermission> permissions) throws IOException {
        addPermissionsForUsersToJob(Set.of(userLogin), folderName, jobName, permissions);
    }

    /**
     * Adds all Jenkins job permissions for the specific Jenkins users to the job. This function does not overwrite
     * permissions that have already been given.
     *
     * @param userLogins the logins of the users that will have the permissions
     * @param jobName the name of the job where the permissions will take affect
     * @param permissions a list of permissions to give to the users
     * @throws IOException thrown when retrieving/updating the Jenkins job failed
     */
    public void addPermissionsForUsersToJob(Set<String> userLogins, String folderName, String jobName, Set<JenkinsJobPermission> permissions) throws IOException {
        var jobConfigDocument = getJobConfigXmlDocument(folderName, jobName);
        JenkinsJobPermissionsUtils.addPermissionsToDocument(jobConfigDocument, permissions, userLogins);
        jenkinsServer.updateJob(jobName, jobConfigDocument.toString(), useCrumb);
    }

    /**
     * Removes the permissions from the user for the specific Jenkins job.
     *
     * @param userLogin the login of the user to remove the permissions
     * @param jobName the name of the job where the permissions will be removed
     * @param permissionsToRemove a list of permissions to remove from the user
     * @throws IOException thrown when retrieving/updating the Jenkins job failed
     */
    public void removePermissionsFromUserOfJob(String userLogin, String folderName, String jobName, Set<JenkinsJobPermission> permissionsToRemove) throws IOException {
        removePermissionsFromUsersForJob(Set.of(userLogin), folderName, jobName, permissionsToRemove);
    }

    /**
     * Removes the permissions from the users for the specific Jenkins job.
     *
     * @param userLogins the logins of the users to remove the permissions
     * @param jobName the name of the job where the permissions will be removed
     * @param permissionsToRemove a list of permissions to remove from the users
     * @throws IOException thrown when retrieving/updating the Jenkins job failed
     */
    public void removePermissionsFromUsersForJob(Set<String> userLogins, String folderName, String jobName, Set<JenkinsJobPermission> permissionsToRemove) throws IOException {
        var jobConfigDocument = getJobConfigXmlDocument(folderName, jobName);
        JenkinsJobPermissionsUtils.removePermissionsFromDocument(jobConfigDocument, permissionsToRemove, userLogins);
        jenkinsServer.updateJob(jobName, jobConfigDocument.toString(), useCrumb);
    }

    /**
     * Fetches the configuration file for the specified Jenkins job as an ml
     * document.
     * @param jobName the name of the Jenkins job to fetch the configuration file
     * @return the job configuration file as an xml document
     * @throws IOException thrown when retrieving/updating the Jenkins job failed
     */
    private Document getJobConfigXmlDocument(String folderName, String jobName) throws IOException {
        var jobXml = "";

        if (folderName != null && !folderName.isEmpty()) {
            var job = jenkinsServer.getJob(folderName);
            var folder = jenkinsServer.getFolderJob(job);
            jobXml = jenkinsServer.getJobXml(folder.orNull(), jobName);
        }
        else {
            jobXml = jenkinsServer.getJobXml(jobName);
        }

        // Parse the config xml file for the job and insert the permissions into it.
        var document = Jsoup.parse(jobXml, "", Parser.xmlParser());
        document.outputSettings().indentAmount(0).prettyPrint(false);

        return document;
    }
}
