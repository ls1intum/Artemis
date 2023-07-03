package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.transform.TransformerException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import de.tum.in.www1.artemis.service.util.XmlFileUtils;

class JenkinsJobPermissionsUtilsTest {

    @Test
    void testRemovePermissionsFromFolder() throws TransformerException {
        final Document folderConfig = XmlFileUtils.readFromString("""
                <?xml version='1.1' encoding='UTF-8'?>
                <com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder@6.729.v2b_9d1a_74d673">
                  <actions/>
                  <description></description>
                  <properties>
                    <com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
                      <inheritanceStrategy class="org.jenkinsci.plugins.matrixauth.inheritance.InheritParentStrategy"/>
                        <permission>hudson.model.Item.Build:instructor1</permission>
                        <permission>hudson.model.Item.Cancel:instructor1</permission>
                        <permission>hudson.model.Item.Configure:instructor1</permission>
                        <permission>hudson.model.Item.Create:instructor1</permission>
                        <permission>hudson.model.Item.Delete:instructor1</permission>
                        <permission>hudson.model.Item.Read:instructor1</permission>
                        <permission>hudson.model.Item.Workspace:instructor1</permission>
                        <permission>hudson.model.Run.Delete:instructor1</permission>
                        <permission>hudson.model.Run.Replay:instructor1</permission>
                        <permission>hudson.model.Run.Update:instructor1</permission>
                        <permission>hudson.scm.SCM.Tag:instructor1</permission>
                        <permission>USER:hudson.model.Item.Build:instructor1</permission>
                        <permission>USER:hudson.model.Item.Cancel:instructor1</permission>
                        <permission>USER:hudson.model.Item.Configure:instructor1</permission>
                        <permission>USER:hudson.model.Item.Create:instructor1</permission>
                        <permission>USER:hudson.model.Item.Delete:instructor1</permission>
                        <permission>USER:hudson.model.Item.Read:instructor1</permission>
                        <permission>USER:hudson.model.Item.Workspace:instructor1</permission>
                        <permission>USER:hudson.model.Run.Delete:instructor1</permission>
                        <permission>USER:hudson.model.Run.Replay:instructor1</permission>
                        <permission>USER:hudson.model.Run.Update:instructor1</permission>
                        <permission>USER:hudson.scm.SCM.Tag:instructor1</permission>
                    </com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>
                  </properties>
                </com.cloudbees.hudson.plugins.folder.Folder>
                """);
        final Set<JenkinsJobPermission> allPermissions = Arrays.stream(JenkinsJobPermission.values()).collect(Collectors.toUnmodifiableSet());

        JenkinsJobPermissionsUtils.removePermissionsFromFolder(folderConfig, allPermissions, Set.of("instructor1"));

        final var updatedPermissions = folderConfig.getElementsByTagName("permission");
        assertThat(updatedPermissions.getLength()).as("Document should contain no permissions:\n" + XmlFileUtils.writeToString(folderConfig)).isZero();
    }

    @Test
    void testAddPermissionsToFolder() {
        final Document folderConfig = XmlFileUtils.readFromString("""
                <?xml version='1.1' encoding='UTF-8'?>
                <com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder@6.729.v2b_9d1a_74d673">
                  <actions/>
                  <description></description>
                  <properties>
                  </properties>
                </com.cloudbees.hudson.plugins.folder.Folder>
                """);
        final Set<JenkinsJobPermission> permissions = Set.of(JenkinsJobPermission.JOB_CREATE, JenkinsJobPermission.RUN_DELETE);

        JenkinsJobPermissionsUtils.addPermissionsToFolder(folderConfig, permissions, Set.of("instructor1"));

        final var createdPermissions = folderConfig.getElementsByTagName("permission");
        assertThat(createdPermissions.getLength()).isEqualTo(2);

        final var actualPermissions = getPermissions(folderConfig);
        final var expectedPermissions = Set.of(getPermission(JenkinsJobPermission.JOB_CREATE, "instructor1"), getPermission(JenkinsJobPermission.RUN_DELETE, "instructor1"));
        assertThat(actualPermissions).hasSameElementsAs(expectedPermissions);
    }

    private static String getPermission(JenkinsJobPermission permission, String username) {
        return String.format("USER:%s:%s", permission.getName(), username);
    }

    private static Set<String> getPermissions(final Document document) {
        final Set<String> permissionValues = new HashSet<>();
        final NodeList permissions = document.getElementsByTagName("permission");

        for (int i = 0; i < permissions.getLength(); ++i) {
            final String permissionValue = permissions.item(i).getTextContent();
            permissionValues.add(permissionValue);
        }

        return permissionValues;
    }
}
