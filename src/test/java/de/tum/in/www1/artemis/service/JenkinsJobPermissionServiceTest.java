package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.Set;

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

    private static final String TEST_PREFIX = "jenkinsjobpermservice";

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
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testAddInstructorAndEditorAndTAPermissionsToUsersForFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        Set<String> taLogins = Set.of(TEST_PREFIX + "ta1");
        var permissionsToRemove = Set.of(JenkinsJobPermission.values());

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(permissionsToRemove), eq(taLogins)))
                .thenThrow(DOMException.class);

        assertThatIOException().isThrownBy(() -> jenkinsJobPermissionsService.addInstructorAndEditorAndTAPermissionsToUsersForFolder(Set.of(TEST_PREFIX + "ta1"),
                Set.of(TEST_PREFIX + "editor1"), Set.of(TEST_PREFIX + "instructor1"), folderName))
                .withMessageStartingWith("Cannot add instructor, editor, and/or ta permissions to users for folder");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testAddTeachingAssistantPermissionsToUserForFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        String taLogin = TEST_PREFIX + "ta1";
        var permissionsToRemove = Set.of(JenkinsJobPermission.values());

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(permissionsToRemove), eq(Set.of(taLogin))))
                .thenThrow(DOMException.class);

        assertThatIOException().isThrownBy(() -> jenkinsJobPermissionsService.addTeachingAssistantPermissionsToUserForFolder(taLogin, folderName))
                .withMessageStartingWith("Cannot add ta permissions to user for folder");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testAddPermissionsForUsersToFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        Set<JenkinsJobPermission> taPermissions = JenkinsJobPermission.getTeachingAssistantPermissions();
        Set<String> taLogins = Set.of(TEST_PREFIX + "ta1");

        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.addPermissionsToFolder(any(), eq(taPermissions), eq(taLogins))).thenThrow(DOMException.class);

        assertThatIOException().isThrownBy(() -> jenkinsJobPermissionsService.addPermissionsForUsersToFolder(taLogins, folderName, taPermissions))
                .withMessageStartingWith("Cannot add permissions to users for folder:");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRemovePermissionsFromUserOfFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        Set<JenkinsJobPermission> taPermissions = JenkinsJobPermission.getTeachingAssistantPermissions();
        String taLogin = TEST_PREFIX + "ta1";

        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(taPermissions), eq(Set.of(taLogin))))
                .thenThrow(DOMException.class);

        assertThatIOException().isThrownBy(() -> jenkinsJobPermissionsService.removePermissionsFromUserOfFolder(taLogin, folderName, taPermissions))
                .withMessageStartingWith("Cannot remove permissions to user for folder:");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRemovePermissionsFromUsersForFolderThrowIOExceptionOnXmlError() throws IOException {
        String folderName = "JenkinsFolder";
        Set<JenkinsJobPermission> taPermissions = JenkinsJobPermission.getTeachingAssistantPermissions();
        Set<String> taLogins = Set.of(TEST_PREFIX + "ta1");

        jenkinsRequestMockProvider.mockGetFolderConfig(folderName);

        mockedJenkinsJobPermissionsUtils.when(() -> JenkinsJobPermissionsUtils.removePermissionsFromFolder(any(Document.class), eq(taPermissions), eq(taLogins)))
                .thenThrow(DOMException.class);

        assertThatIOException().isThrownBy(() -> jenkinsJobPermissionsService.removePermissionsFromUsersForFolder(taLogins, folderName, taPermissions))
                .withMessageStartingWith("Cannot remove permissions to user for folder:");
    }
}
