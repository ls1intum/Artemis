package de.tum.in.www1.artemis.usermanagement.connector;

import static de.tum.in.www1.artemis.util.FileUtils.loadFileFromResources;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.JenkinsUserDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.in.www1.artemis.service.user.PasswordService;

@Component
@Profile("jenkins")
public class JenkinsRequestMockProvider {

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsServerUrl;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer shortTimeoutMockServer;

    @SpyBean
    @InjectMocks
    private JenkinsServer jenkinsServer;

    @SpyBean
    @InjectMocks
    private JenkinsJobPermissionsService jenkinsJobPermissionsService;

    @SpyBean
    @InjectMocks
    private PasswordService passwordService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    public JenkinsRequestMockProvider(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.restTemplate.setInterceptors(List.of());
        this.shortTimeoutRestTemplate.setInterceptors(List.of());
    }

    public void enableMockingOfRequests(JenkinsServer jenkinsServer) {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).bufferContent().build();
        shortTimeoutMockServer = MockRestServiceServer.bindTo(shortTimeoutRestTemplate).ignoreExpectOrder(true).bufferContent().build();
        this.jenkinsServer = jenkinsServer;
        MockitoAnnotations.openMocks(this);
    }

    public void reset() {
        mockServer.reset();
        shortTimeoutMockServer.reset();
    }

    public void mockGetFolderConfig(String folderName) throws IOException {
        doReturn(new JobWithDetails()).when(jenkinsServer).getJob(folderName);
        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");
        doReturn(mockXml).when(jenkinsServer).getJobXml(eq(folderName));
    }

    public void mockUpdateUserAndGroups(String oldLogin, User user, Set<String> groupsToAdd, Set<String> groupsToRemove, boolean userExistsInJenkins)
            throws IOException, URISyntaxException {
        if (!oldLogin.equals(user.getLogin())) {
            mockUpdateUserLogin(oldLogin, user);
        }
        else {
            mockUpdateUser(user, userExistsInJenkins);
        }
        mockRemoveUserFromGroups(groupsToRemove, false);
        mockAddUsersToGroups(groupsToAdd, false);
    }

    private void mockUpdateUser(User user, boolean userExists) throws URISyntaxException, IOException {
        mockGetUser(user.getLogin(), userExists, false);

        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);
        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);

        mockDeleteUser(user, userExists, false);
        mockCreateUser(user, false, false, false);
    }

    private void mockUpdateUserLogin(String oldLogin, User user) throws IOException, URISyntaxException {
        if (oldLogin.equals(user.getLogin())) {
            return;
        }

        var oldUser = new User();
        oldUser.setLogin(oldLogin);
        oldUser.setGroups(user.getGroups());
        mockDeleteUser(oldUser, true, false);
        mockCreateUser(user, false, false, false);
    }

    public void mockDeleteUser(User user, boolean userExistsInUserManagement, boolean shouldFailToDelete) throws IOException, URISyntaxException {
        mockGetUser(user.getLogin(), userExistsInUserManagement, false);

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("user", user.getLogin(), "doDelete").build().toUri();
        var status = shouldFailToDelete ? HttpStatus.NOT_FOUND : HttpStatus.FOUND;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(status));

        mockRemoveUserFromGroups(user.getGroups(), false);
    }

    private void mockGetUser(String userLogin, boolean userExists, boolean shouldFailToGetUser) throws URISyntaxException, JsonProcessingException {
        var jenkinsUser = new JenkinsUserDTO();
        jenkinsUser.id = userLogin;

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("user", userLogin, "api", "json").build().toUri();
        if (userExists) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.FOUND).body(mapper.writeValueAsString(jenkinsUser)).contentType(MediaType.APPLICATION_JSON));
        }
        else if (shouldFailToGetUser) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        }
    }

    public void mockRemoveUserFromGroups(Set<String> groupsToRemove, boolean shouldFail) throws IOException {
        if (groupsToRemove.isEmpty()) {
            return;
        }

        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groupsToRemove);
        for (ProgrammingExercise exercise : exercises) {
            var folderName = exercise.getProjectKey();
            mockRemovePermissionsFromUserOfFolder(folderName, shouldFail);
        }
    }

    private void mockRemovePermissionsFromUserOfFolder(String folderName, boolean shouldFail) throws IOException {
        if (shouldFail) {
            doThrow(IOException.class).when(jenkinsJobPermissionsService).removePermissionsFromUserOfFolder(anyString(), eq(folderName), any());
        }
        else {
            doNothing().when(jenkinsJobPermissionsService).removePermissionsFromUserOfFolder(anyString(), eq(folderName), any());
        }
    }

    public void mockCreateUser(User user, boolean userExistsInCi, boolean shouldFail, boolean shouldFailToGetUser) throws URISyntaxException, IOException {
        mockGetUser(user.getLogin(), userExistsInCi, shouldFailToGetUser);

        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);
        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("securityRealm", "createAccountByAdmin").build().toUri();
        var status = shouldFail ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.FOUND;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(status));

        mockAddUsersToGroups(user.getGroups(), false);
    }

    public void mockAddUsersToGroups(Set<String> groups, boolean shouldFail) throws IOException {
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        for (ProgrammingExercise exercise : exercises) {
            var jobName = exercise.getProjectKey();
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();

            if (groups.contains(course.getInstructorGroupName()) || groups.contains(course.getEditorGroupName()) || groups.contains(course.getTeachingAssistantGroupName())) {
                mockGetFolderConfig(jobName);
                if (shouldFail) {
                    doThrow(IOException.class).when(jenkinsServer).updateJob(eq(jobName), anyString(), eq(useCrumb));
                }
                else {
                    doNothing().when(jenkinsServer).updateJob(eq(jobName), anyString(), eq(useCrumb));
                }
            }
        }
    }

}
