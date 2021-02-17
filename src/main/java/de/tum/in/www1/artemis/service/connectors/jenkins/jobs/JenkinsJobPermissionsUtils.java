package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsJobPermission;

public class JenkinsJobPermissionsUtils {

    /**
     * Removes the specified permissions belonging to the user from the xml document. Permission
     * elements must be children of the AuthorizationMatrixProperty element. Doesn't do anything
     * if AuthorizationMatrixProperty is missing.
     *
     * @param document The xml document
     * @param permissionsToRemove  a list of permissions to remove from the user
     * @param userLogin  the login of the user to remove the permissions from
     */
    public static void removePermissionsFromDocument(Document document, Set<JenkinsJobPermission> permissionsToRemove, String userLogin) {
        removePermissionsFromDocument(document, permissionsToRemove, Set.of(userLogin));
    }

    /**
     * Removes the specified permissions belonging to all specified users from the xml document. Permission
     * elements must be children of the AuthorizationMatrixProperty element. Doesn't do anything
     * if AuthorizationMatrixProperty is missing.
     *
     * @param document The xml document
     * @param permissionsToRemove  a list of permissions to remove from the user
     * @param userLogins  the logins of the users to remove the permissions from
     */
    public static void removePermissionsFromDocument(Document document, Set<JenkinsJobPermission> permissionsToRemove, Set<String> userLogins) {
        var authorizationMatrixTagName = "com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty";
        var authorizationMatrixElement = document.getElementsByTag(authorizationMatrixTagName).first();
        if (authorizationMatrixElement == null) {
            return;
        }

        permissionsToRemove.forEach(jenkinsJobPermission -> {
            userLogins.forEach(userLogin -> {
                // The permission in the xml node has the format: com.jenkins.job.permission:user-login
                var permission = jenkinsJobPermission.getName() + ":" + userLogin;
                authorizationMatrixElement.getElementsContainingOwnText(permission).remove();
            });
        });
    }

    /**
     * Adds all jenkinsJobPermissions for all specific Jenkins users into the xml document.
     *
     * @param document the xml document
     * @param jenkinsJobPermissions a list of Jenkins job permissions to be added for the specific user
     * @param userLogins the login names of the users
     */
    public static void addPermissionsToDocument(Document document, Set<JenkinsJobPermission> jenkinsJobPermissions, Set<String> userLogins) {
        // The authorization matrix is an element that holds the permissions for a specific
        // job.
        var authorizationMatrixElement = JenkinsJobPermissionsUtils.getOrCreateAuthorizationMatrixPropertyElement(document);

        JenkinsJobPermissionsUtils.addPermissionsToAuthorizationMatrix(authorizationMatrixElement, jenkinsJobPermissions, userLogins);
        JenkinsJobPermissionsUtils.addAuthorizationMatrixToDocument(authorizationMatrixElement, document);
    }

    /**
     * Retrieves the AuthorizationMatrixProperty element from the document if it exists or creates a new one
     * pre-configured with matrixauth.inheritance.InheritParentStrategy.
     * @param document The xml document
     * @return AuthorizationMatrixProperty element
     */
    private static Element getOrCreateAuthorizationMatrixPropertyElement(Document document) {
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
     * Adds all jenkinsJobPermissions for all specific Jenkins users into the authorizationMatrixElement.
     * The resulting output element has the following format:
     * <pre>
     * {@code
     *      <com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
     *          ...existing permissions
     *          <permission>hudson.model.the.jenkins.permission1:userLogin1</permission>
     *          ...
     *          <permission>hudson.model.the.jenkins.permissionn:userLogin1</permission>
     *          ...
     *          <permission>hudson.model.the.jenkins.permissionn:userLoginN</permission>
     *      </com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
     * }
     * </pre>
     * @param authorizationMatrixElement the com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty element
     * @param jenkinsJobPermissions      a list of Jenkins job permissions to be added for the specific user
     * @param userLogins                  the login names of the users
     */
    private static void addPermissionsToAuthorizationMatrix(Element authorizationMatrixElement, Set<JenkinsJobPermission> jenkinsJobPermissions, Set<String> userLogins) {
        userLogins.forEach(userLogin -> addPermissionsToAuthorizationMatrix(authorizationMatrixElement, jenkinsJobPermissions, userLogin));
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
    private static void addPermissionsToAuthorizationMatrix(Element authorizationMatrixElement, Set<JenkinsJobPermission> jenkinsJobPermissions, String userLogin) {
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
    private static void addAuthorizationMatrixToDocument(Element authorizationMatrixElement, Document document) {
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
