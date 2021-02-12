package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.offbytwo.jenkins.JenkinsServer;

@Service
@Profile("jenkins")
public class JenkinsJobService {

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final JenkinsServer jenkinsServer;

    public JenkinsJobService(JenkinsServer jenkinsServer) {
        this.jenkinsServer = jenkinsServer;
    }

    /**
     * Adds all Jenkins job permissions for the specific Jenkins user to the job. This function does not overwrite
     * permissions that have already been given.
     *
     * @param userLogin the login of the user that will have the permissions
     * @param jobName the name of the job where the permissions will take affect
     * @param permissions a list of permissions to give to the user
     * @throws IOException exception thrown when retrieving/updating the Jenkins job failed
     */
    public void addPermissionsForUserToJob(String userLogin, String jobName, List<JenkinsJobPermission> permissions) throws IOException {
        var jobXml = jenkinsServer.getJobXml(jobName);

        // Parse the config xml file for the job and insert the permissions into it.
        var document = Jsoup.parse(jobXml, "", Parser.xmlParser());
        document.outputSettings().indentAmount(0).prettyPrint(false);

        // The authorization matrix is an element that holds the permissions for a specific
        // job.
        var authorizationMatrixElement = getOrCreateAuthorizationMatrixPropertyElement(document);

        addPermissionsToAuthorizationMatrix(authorizationMatrixElement, permissions, userLogin);
        addAuthorizationMatrixToDocument(authorizationMatrixElement, document);

        jenkinsServer.updateJob(jobName, document.toString(), useCrumb);
    }

    /**
     * Removes the permissions from the user for the specific Jenkins job.
     *
     * @param userLogin the login of the user to remove the permissions
     * @param jobName the name of the job where the permissions will be removed
     * @param permissionsToRemove a list of permissions to remove from the user
     * @throws IOException exception thrown when retrieving/updating the Jenkins job failed
     */
    public void removePermissionsFromUserOfJob(String userLogin, String jobName, List<JenkinsJobPermission> permissionsToRemove) throws IOException {
        var jobXml = jenkinsServer.getJobXml(jobName);

        // Parse the config xml file for the job and insert the permissions into it.
        var document = Jsoup.parse(jobXml, "", Parser.xmlParser());
        document.outputSettings().indentAmount(0).prettyPrint(false);

        // The authorization matrix is an element that holds the permissions for a specific
        // job.
        removePermissionsFromAuthorizationMatrix(document, permissionsToRemove, userLogin);

        jenkinsServer.updateJob(jobName, document.toString(), useCrumb);
    }

    /**
     *
     * @param document
     * @param permissionsToRemove
     * @param userLogin
     */
    private void removePermissionsFromAuthorizationMatrix(Document document, List<JenkinsJobPermission> permissionsToRemove, String userLogin) {
        var authorizationMatrixTagName = "com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty";
        var authorizationMatrixElement = document.getElementsByTag(authorizationMatrixTagName).first();
        if (authorizationMatrixElement == null) {
            return;
        }

        permissionsToRemove.forEach(jenkinsJobPermission -> {
            // The permission in the xml node has the format: com.jenkins.job.permission:user-login
            var permission = jenkinsJobPermission.getName() + ":" + userLogin;
            authorizationMatrixElement.getElementsContainingOwnText(permission).remove();
        });
    }

    /**
     * Retrieves the AuthorizationMatrixProperty element from the document if it exists or creates a new one
     * pre-configured with matrixauth.inheritance.InheritParentStrategy.
     * @param document The xml document
     * @return AuthorizationMatrixProperty element
     */
    private Element getOrCreateAuthorizationMatrixPropertyElement(Document document) {
        var authorizationMatrixTagName = "com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty";
        var authorizationMatrixElement = document.getElementsByTag(authorizationMatrixTagName).first();
        if (authorizationMatrixElement != null) {
            return authorizationMatrixElement;
        }

        // Create the element
        var strategyElement = new Element("inheritanceStrategy").addClass("org.jenkinsci.plugins.matrixauth.inheritance.InheritParentStrategy");
        authorizationMatrixElement = new Element(authorizationMatrixTagName);
        strategyElement.appendTo(authorizationMatrixElement);
        return authorizationMatrixElement;
    }

    /**
     * Adds all jenkinsJobPermissions specified for the specific Jenkins user into the authorizationMatrixElement.
     * The resulting output element has the following format:
     * <pre>
     * {@code
     *      <com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
     *          ...existing permissions
     *          <permission>hudson.model.the.jenkins.permission1:userLogin</permission>
     *          ...
     *          <permission>hudson.model.the.jenkins.permissionn:userLogin</permission>
     *      </com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
     * }
     * </pre>
     * @param authorizationMatrixElement the com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty element
     * @param jenkinsJobPermissions      a list of Jenkins job permissions to be added for the specific user
     * @param userLogin                  the login name of the user
     */
    private void addPermissionsToAuthorizationMatrix(Element authorizationMatrixElement, List<JenkinsJobPermission> jenkinsJobPermissions, String userLogin) {
        var existingPermissionElements = authorizationMatrixElement.getElementsByTag("permission");
        jenkinsJobPermissions.forEach(jenkinsJobPermission -> {
            // The permission in the xml node has the format: com.jenkins.job.permission:user-login
            var permission = jenkinsJobPermission.getName() + ":" + userLogin;

            // Add the permission if it doesn't exist.
            var permissionDoesntExist = existingPermissionElements.stream().noneMatch(element -> element.text().equals(permission));
            if (permissionDoesntExist) {
                // Permission element has format <permission>com.jenkins.job.permission:user-login</permission>
                var permissionElement = new Element("permission").appendText(permission);
                permissionElement.appendTo(authorizationMatrixElement);
            }
        });
    }

    /**
     * Adds the authorizationMatrixElement into the document. The function checks the document if the properties
     * element exist and creates one if it doesn't. The authorizationMatrixElement must be a child if this tag.
     *
     * @param authorizationMatrixElement the com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty element
     * @param document                   the Jenkins Job config.xml
     */
    private void addAuthorizationMatrixToDocument(Element authorizationMatrixElement, Document document) {
        // The authorization matrix is stored inside the <properties/> tag within the document. Either find it
        // or create a new one.
        var propertyElement = document.getElementsByTag("properties").first();
        if (propertyElement == null) {
            propertyElement = new Element("properties");
            propertyElement.appendTo(document);
        }

        authorizationMatrixElement.appendTo(propertyElement);
    }
}
