package de.tum.in.www1.artemis.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermission;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermissionsUtils;

class JenkinsJobPermissionServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private JenkinsJobPermissionsService jenkinsJobPermissionsService;

    private static MockedStatic<JenkinsJobPermissionsUtils> mockedJenkinsJobPermissionsUtils;

    @BeforeEach
    void initTestCase() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        mockedJenkinsJobPermissionsUtils = mockStatic(JenkinsJobPermissionsUtils.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        mockedJenkinsJobPermissionsUtils.close();
    }

    @Test
    @WithMockUser(username = "student1")
    void testAddInstructorAndEditorAndTAPermissionsToUsersForFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        Set<String> taLogins = Set.of("ta1");
        var permissionsToRemove = Set.of(JenkinsJobPermission.values());

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(permissionsToRemove), eq(taLogins)))
                .thenThrow(DOMException.class);

        Exception exception = assertThrows(IOException.class, () -> {
            jenkinsJobPermissionsService.addInstructorAndEditorAndTAPermissionsToUsersForFolder(Set.of("ta1"), Set.of("editor1"), Set.of("instructor1"), folderName);
        });
        Assertions.assertThat(exception.getMessage()).startsWith("Cannot add instructor, editor, and/or ta permissions to users for folder");
    }

    @Test
    @WithMockUser(username = "student1")
    void testAddTeachingAssistantPermissionsToUserForFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        String taLogin = "ta1";
        var permissionsToRemove = Set.of(JenkinsJobPermission.values());

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(permissionsToRemove), eq(Set.of(taLogin))))
                .thenThrow(DOMException.class);

        Exception exception = assertThrows(IOException.class, () -> {
            jenkinsJobPermissionsService.addTeachingAssistantPermissionsToUserForFolder(taLogin, folderName);
        });
        Assertions.assertThat(exception.getMessage()).startsWith("Cannot add ta permissions to user for folder");
    }

    @Test
    @WithMockUser(username = "student1")
    void testAddPermissionsForUsersToFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        Set<JenkinsJobPermission> taPermissions = JenkinsJobPermission.getTeachingAssistantPermissions();
        Set<String> taLogins = Set.of("ta1");

        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.addPermissionsToFolder(any(), eq(taPermissions), eq(taLogins))).thenThrow(DOMException.class);

        Exception exception = assertThrows(IOException.class, () -> {
            jenkinsJobPermissionsService.addPermissionsForUsersToFolder(taLogins, folderName, taPermissions);
        });
        Assertions.assertThat(exception.getMessage()).startsWith("Cannot add permissions to users for folder:");
    }

    @Test
    @WithMockUser(username = "student1")
    void testRemovePermissionsFromUserOfFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        Set<JenkinsJobPermission> taPermissions = JenkinsJobPermission.getTeachingAssistantPermissions();
        String taLogin = "ta1";

        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(taPermissions), eq(Set.of(taLogin))))
                .thenThrow(DOMException.class);

        Exception exception = assertThrows(IOException.class, () -> {
            jenkinsJobPermissionsService.removePermissionsFromUserOfFolder(taLogin, folderName, taPermissions);
        });
        Assertions.assertThat(exception.getMessage()).startsWith("Cannot remove permissions to user for folder:");
    }

    @Test
    @WithMockUser(username = "student1")
    void testRemovePermissionsFromUsersForFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        Set<JenkinsJobPermission> taPermissions = JenkinsJobPermission.getTeachingAssistantPermissions();
        Set<String> taLogins = Set.of("ta1");

        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(taPermissions), eq(taLogins)))
                .thenThrow(DOMException.class);

        Exception exception = assertThrows(IOException.class, () -> {
            jenkinsJobPermissionsService.removePermissionsFromUsersForFolder(taLogins, folderName, taPermissions);
        });
        Assertions.assertThat(exception.getMessage()).startsWith("Cannot remove permissions to user for folder:");
    }
}
