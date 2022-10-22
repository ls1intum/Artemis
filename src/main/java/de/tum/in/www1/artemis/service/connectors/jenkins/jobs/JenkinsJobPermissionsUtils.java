package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

import java.util.Set;

import org.w3c.dom.*;

public class JenkinsJobPermissionsUtils {

    /**
     * Modern versions (>= 3.0) of the Matrix Authorization Strategy Plugin in
     * Jenkins use a prefix to discern between permissions affecting individual
     * users or groups.
     */
    private static final String USER_PERMISSIONS_PREFIX = "USER:";

    private JenkinsJobPermissionsUtils() {
    }

    public static void removePermissionsFromFolder(Document jobConfig, Set<JenkinsJobPermission> permissionsToRemove, Set<String> userLogins) throws DOMException {
        var folderAuthorizationMatrix = "com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty";
        removePermissionsFromElement(folderAuthorizationMatrix, jobConfig, permissionsToRemove, userLogins);
    }

    public static void removePermissionsFromJob(Document jobConfig, Set<JenkinsJobPermission> permissionsToRemove, Set<String> userLogins) throws DOMException {
        var jobAuthorizationMatrix = "hudson.security.AuthorizationMatrixProperty";
        removePermissionsFromElement(jobAuthorizationMatrix, jobConfig, permissionsToRemove, userLogins);
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
    private static void removePermissionsFromElement(String elementTagName, Document document, Set<JenkinsJobPermission> permissionsToRemove, Set<String> userLogins)
            throws DOMException {
        Node authorizationMatrixElement = document.getElementsByTagName(elementTagName).item(0);
        if (authorizationMatrixElement == null) {
            return;
        }

        permissionsToRemove.forEach(jenkinsJobPermission -> userLogins.forEach(userLogin -> {
            // The permission in the xml node has the format: com.jenkins.job.permission:user-login
            String permission = jenkinsJobPermission.getName() + ":" + userLogin;
            // old jobs might still use the permission without the prefix
            removePermission(authorizationMatrixElement, permission);
            removePermission(authorizationMatrixElement, USER_PERMISSIONS_PREFIX + permission);
        }));
    }

    /**
     * Removes the permission element from the authorization matrix.
     *
     * @param authorizationMatrix The authorization matrix node
     * @param permission the permission to remove
     */
    private static void removePermission(Node authorizationMatrix, String permission) throws DOMException {
        NodeList permissionNodes = authorizationMatrix.getChildNodes();
        int nodeCount = permissionNodes.getLength();
        for (int i = 0; i < nodeCount; i++) {
            Node permissionNode = permissionNodes.item(i);
            if (permissionNode.getTextContent().equals(permission)) {
                authorizationMatrix.removeChild(permissionNode);
                return;
            }
        }
    }

    public static void addPermissionsToFolder(Document folderConfig, Set<JenkinsJobPermission> jenkinsJobPermissions, Set<String> userLogins) throws DOMException {
        var folderAuthorizationMatrix = "com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty";
        addPermissionsToDocument(folderAuthorizationMatrix, folderConfig, jenkinsJobPermissions, userLogins);
    }

    public static void addPermissionsToJob(Document jobConfig, Set<JenkinsJobPermission> jenkinsJobPermissions, Set<String> userLogins) throws DOMException {
        var jobAuthorizationMatrix = "hudson.security.AuthorizationMatrixProperty";
        addPermissionsToDocument(jobAuthorizationMatrix, jobConfig, jenkinsJobPermissions, userLogins);
    }

    /**
     * Adds all jenkinsJobPermissions for all specific Jenkins users into the xml document.
     *
     * @param document the xml document
     * @param jenkinsJobPermissions a list of Jenkins job permissions to be added for the specific user
     * @param userLogins the login names of the users
     */
    private static void addPermissionsToDocument(String elementTagName, Document document, Set<JenkinsJobPermission> jenkinsJobPermissions, Set<String> userLogins)
            throws DOMException {
        var authorizationMatrixElement = JenkinsJobPermissionsUtils.getOrCreateAuthorizationMatrixPropertyElement(elementTagName, document);
        userLogins.forEach(userLogin -> addPermissionsToAuthorizationMatrix(document, authorizationMatrixElement, jenkinsJobPermissions, userLogin));
        JenkinsJobPermissionsUtils.addAuthorizationMatrixToDocument(authorizationMatrixElement, document);
    }

    /**
     * Retrieves the AuthorizationMatrixProperty element from the document if it exists or creates a new one
     * pre-configured with matrixauth.inheritance.InheritParentStrategy.
     * @param document The xml document
     * @return AuthorizationMatrixProperty element
     */
    private static Element getOrCreateAuthorizationMatrixPropertyElement(String authorizationMatrixTagName, Document document) throws DOMException {
        Element authorizationMatrixElement = (Element) document.getElementsByTagName(authorizationMatrixTagName).item(0);
        if (authorizationMatrixElement != null) {
            return authorizationMatrixElement;
        }

        // Create the element
        Element strategyElement = document.createElement("inheritanceStrategy");
        strategyElement.setAttribute("class", "org.jenkinsci.plugins.matrixauth.inheritance.InheritParentStrategy");

        authorizationMatrixElement = document.createElement(authorizationMatrixTagName);
        authorizationMatrixElement.appendChild(strategyElement);
        return authorizationMatrixElement;
    }

    /**
     * Adds all jenkinsJobPermissions specified for the specific Jenkins user into the authorizationMatrixElement.
     * The resulting output element has the following format:
     * <pre>
     * {@code
     *      <com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
     *          ...existing permissions
     *          <permission>USER:hudson.model.the.jenkins.permission1:userLogin</permission>
     *          ...
     *          <permission>USER:hudson.model.the.jenkins.permission:userLogin</permission>
     *      </com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
     * }
     * </pre>
     * @param authorizationMatrixElement the com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty element
     * @param jenkinsJobPermissions      a list of Jenkins job permissions to be added for the specific user
     * @param userLogin                  the login name of the user
     */
    private static void addPermissionsToAuthorizationMatrix(Document document, Element authorizationMatrixElement, Set<JenkinsJobPermission> jenkinsJobPermissions,
            String userLogin) throws DOMException {
        NodeList existingPermissionElements = authorizationMatrixElement.getElementsByTagName("permission");
        jenkinsJobPermissions.forEach(jenkinsJobPermission -> {
            // The permission in the xml node has the format: com.jenkins.job.permission:user-login
            String permission = USER_PERMISSIONS_PREFIX + jenkinsJobPermission.getName() + ":" + userLogin;

            // Add the permission if it doesn't exist.
            boolean permissionExists = permissionExistInPermissionList(existingPermissionElements, permission);
            if (!permissionExists) {
                // Permission element has format <permission>USER:com.jenkins.job.permission:user-login</permission>
                Element permissionElement = document.createElement("permission");
                permissionElement.setTextContent(permission);
                authorizationMatrixElement.appendChild(permissionElement);
            }
        });
    }

    /**
     * Iterates over the permission node list and checks if the specified permission exists as the text
     * content of the node.
     *
     * @param permissionList The node list containing permission elements
     * @param permission the permission
     * @return if the list contains permissions
     */
    private static boolean permissionExistInPermissionList(NodeList permissionList, String permission) throws DOMException {
        int nodeCount = permissionList.getLength();
        for (int i = 0; i < nodeCount; i++) {
            Node permissionNode = permissionList.item(i);
            if (permissionNode.getTextContent().equals(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the authorizationMatrixElement into the document. The function checks the document if the properties
     * element exist and creates one if it doesn't. The authorizationMatrixElement must be a child if this tag.
     *
     * @param authorizationMatrixElement the com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty element
     * @param document                   the Jenkins Job config.xml
     */
    private static void addAuthorizationMatrixToDocument(Element authorizationMatrixElement, Document document) throws DOMException {
        // The authorization matrix is stored inside the <properties/> tag within the document. Either find it
        // or create a new one.
        NodeList propertyElements = document.getElementsByTagName("properties");

        Node propertiesElement = propertyElements.item(0);
        if (propertiesElement == null) {
            propertiesElement = document.createElement("properties");
            document.appendChild(propertiesElement);
        }

        propertiesElement.appendChild(authorizationMatrixElement);
    }
}
