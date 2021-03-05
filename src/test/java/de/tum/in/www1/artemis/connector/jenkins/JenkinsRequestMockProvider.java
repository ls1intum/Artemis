package de.tum.in.www1.artemis.connector.jenkins;

import static de.tum.in.www1.artemis.util.FileUtils.loadFileFromResources;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import com.google.common.base.Optional;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.model.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.JenkinsUserDTO;
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

    @SpyBean
    @InjectMocks
    private JenkinsServer jenkinsServer;

    @Mock
    private JenkinsHttpClient jenkinsClient;

    @SpyBean
    @InjectMocks
    private PasswordService passwordService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    public JenkinsRequestMockProvider(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        // We remove JenkinsAuthorizationInterceptor because the tests hit the intercept() method
        // which has its' own instance of RestTemplate (in order to get a crumb(. Since that template
        // isn't mocked, it will throw an exception.
        // TODO: Find a way to either mock the interceptor or mock its RestTemplate
        this.restTemplate.setInterceptors(List.of());
    }

    public void enableMockingOfRequests(JenkinsServer jenkinsServer) {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).bufferContent().build();
        this.jenkinsServer = jenkinsServer;
        MockitoAnnotations.openMocks(this);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException {
        // TODO: we need to mockRetrieveArtifacts folder(...)
        doNothing().when(jenkinsServer).createFolder(null, exercise.getProjectKey(), useCrumb);
    }

    public void mockCreateBuildPlan(String projectKey, String planKey) throws IOException {
        var jobFolder = projectKey;
        var job = jobFolder + "-" + planKey;
        mockCreateJobInFolder(jobFolder, job);
        mockGivePlanPermissions(jobFolder, job);
        mockTriggerBuild(jobFolder, job, mock(JobWithDetails.class));
    }

    public void mockCreateJobInFolder(String jobFolder, String job) throws IOException {
        var folderJob = new FolderJob();
        mockGetFolderJob(jobFolder, folderJob);
        doNothing().when(jenkinsServer).createJob(any(FolderJob.class), eq(job), anyString(), eq(useCrumb));
    }

    public void mockGivePlanPermissions(String jobFolder, String job) throws IOException {
        // jenkinsJobService.getJobConfig(folderName, jobName)
        mockGetJobConfig(jobFolder, job);

        // jenkinsJobService.updateJob(folderName, jobName, jobConfig);
        mockUpdateJob(jobFolder, job);

        // addInstructorAndTAPermissionsToUsersForFolder(taLogins, instructorLogins, folderName);
        mockGetFolderConfig(jobFolder);
        doNothing().when(jenkinsServer).updateJob(eq(jobFolder), anyString(), eq(useCrumb));
    }

    private void mockGetJobConfig(String folderName, String jobName) throws IOException {
        doReturn(new JobWithDetails()).when(jenkinsServer).getJob(folderName);
        doReturn(Optional.of(new FolderJob())).when(jenkinsServer).getFolderJob(any(JobWithDetails.class));
        doReturn("").when(jenkinsServer).getJobXml(any(FolderJob.class), eq(jobName));
    }

    private void mockGetFolderConfig(String folderName) throws IOException {
        doReturn(new JobWithDetails()).when(jenkinsServer).getJob(folderName);
        doReturn("").when(jenkinsServer).getJobXml(eq(folderName));
    }

    private void mockUpdateJob(String folderName, String jobName) throws IOException {
        if (folderName != null && !folderName.isEmpty()) {
            doReturn(new JobWithDetails()).when(jenkinsServer).getJob(folderName);
            mockGetFolderJob(folderName, new FolderJob());
            doNothing().when(jenkinsServer).updateJob(any(FolderJob.class), eq(jobName), anyString(), eq(useCrumb));
        }
        else {
            doNothing().when(jenkinsServer).updateJob(eq(jobName), anyString(), eq(useCrumb));
        }
    }

    public void mockTriggerBuild() throws IOException {
        ExtractHeader location = new ExtractHeader();
        location.setLocation("mockLocation");
        doReturn(location).when(jenkinsClient).post(anyString(), any(), any(), eq(useCrumb));
    }

    public void mockCheckIfProjectExists(ProgrammingExercise exercise, boolean exists) throws IOException {
        var jobOrNull = exists ? new JobWithDetails() : null;
        doReturn(jobOrNull).when(jenkinsServer).getJob(exercise.getProjectKey());
    }

    public void mockCopyBuildPlan(String sourceProjectKey, String targetProjectKey) throws IOException {
        mockGetJobXmlForBuildPlanWith(sourceProjectKey, "<xml></xml>");
        mockSaveJobXml(targetProjectKey);
    }

    private void mockSaveJobXml(String targetProjectKey) throws IOException {
        mockGetFolderJob(targetProjectKey, new FolderJob());
        doNothing().when(jenkinsServer).createJob(any(), anyString(), anyString(), eq(useCrumb));
    }

    public void mockConfigureBuildPlan(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var planKey = projectKey + "-" + username.toUpperCase();
        mockUpdatePlanRepository(projectKey, planKey);
        mockEnablePlan(projectKey, planKey);
    }

    public void mockUpdatePlanRepository(String projectKey, String planName) throws IOException, URISyntaxException {
        final var mockXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><script><para>pipeline</para></script>";
        mockGetFolderJob(projectKey, new FolderJob());
        mockGetJobXmlForBuildPlanWith(projectKey, mockXml);

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("job", projectKey, "job", planName, "config.xml").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));

        final var job = mock(JobWithDetails.class);
        mockTriggerBuild(projectKey, planName, job);
        mockTriggerBuild(projectKey, planName, job);
    }

    private void mockGetJobXmlForBuildPlanWith(String projectKey, String xmlToReturn) throws IOException {
        mockGetFolderJob(projectKey, new FolderJob());
        doReturn(xmlToReturn).when(jenkinsServer).getJobXml(any(), any());
    }

    private void mockTriggerBuild(String projectKey, String planName, JobWithDetails jobToReturn) throws IOException {
        jobToReturn.setClient(jenkinsClient);
        mockGetJob(projectKey, planName, jobToReturn);

        final var location = new ExtractHeader();
        location.setLocation("mockLocation");
        doReturn(new QueueReference(location.getLocation())).when(jobToReturn).build(useCrumb);
    }

    public void mockEnablePlan(String projectKey, String planKey) throws URISyntaxException, IOException {
        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("job", projectKey, "job", planKey, "enable").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.FOUND));
    }

    public void mockCopyBuildPlanForParticipation(ProgrammingExercise exercise, String username) throws IOException {
        final var projectKey = exercise.getProjectKey();
        mockCopyBuildPlan(projectKey, projectKey);
    }

    private void mockGetJob(String projectKey, String jobName, JobWithDetails jobToReturn) throws IOException {
        final var folder = new FolderJob();
        mockGetFolderJob(projectKey, folder);
        doReturn(jobToReturn).when(jenkinsServer).getJob(folder, jobName);
    }

    private void mockGetFolderJob(String folderName, FolderJob folderJobToReturn) throws IOException {
        final var jobWithDetails = new JobWithDetails();
        doReturn(jobWithDetails).when(jenkinsServer).getJob(folderName);
        doReturn(com.google.common.base.Optional.of(folderJobToReturn)).when(jenkinsServer).getFolderJob(jobWithDetails);
    }

    public void mockGetBuildStatus(ProgrammingExerciseStudentParticipation participation) throws IOException, URISyntaxException {
        final var job = mock(JobWithDetails.class);
        mockGetJob(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId(), job);
        doReturn(false).when(job).isInQueue();

        final var projectKey = participation.getProgrammingExercise().getProjectKey();
        final var planKey = participation.getBuildPlanId();
        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("job", projectKey, "job", planKey).path("/lastBuild/api/json").build(true).toUri();

        final var mockResponse = Map.of("building", false);
        final var body = new ObjectMapper().writeValueAsString(mockResponse);
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(body).contentType(MediaType.APPLICATION_JSON));

    }

    public BuildWithDetails mockGetLatestBuildLogs(ProgrammingExerciseStudentParticipation participation) throws IOException {
        String projectKey = participation.getProgrammingExercise().getProjectKey();
        String buildPlanId = participation.getBuildPlanId();

        final var job = mock(JobWithDetails.class);
        mockGetJob(projectKey, buildPlanId, job);

        final var buildLogResponse = loadFileFromResources("test-data/jenkins-response/failed-build-log.html");

        final var build = mock(Build.class);
        doReturn(build).when(job).getLastBuild();

        final var buildWithDetails = mock(BuildWithDetails.class);
        doReturn(buildWithDetails).when(build).details();

        doReturn(buildLogResponse).when(buildWithDetails).getConsoleOutputHtml();
        return buildWithDetails;

    }

    public void mockUpdateUserAndGroups(String oldLogin, User user, Set<String> groupsToAdd, Set<String> groupsToRemove, boolean userExistsInJenkins)
            throws IOException, URISyntaxException {
        if (!oldLogin.equals(user.getLogin())) {
            mockUpdateUserLogin(oldLogin, user);
        }
        else {
            mockUpdateUser(user, userExistsInJenkins);
        }
        mockRemoveUserFromGroups(groupsToRemove);
        mockAddUsersToGroups(user.getLogin(), groupsToAdd);
    }

    private void mockUpdateUser(User user, boolean userExists) throws URISyntaxException, IOException {
        mockGetUser(user.getLogin(), userExists);

        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);
        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);

        mockDeleteUser(user, userExists);
        mockCreateUser(user);
    }

    private void mockUpdateUserLogin(String oldLogin, User user) throws IOException, URISyntaxException {
        if (oldLogin.equals(user.getLogin())) {
            return;
        }

        var oldUser = new User();
        oldUser.setLogin(oldLogin);
        oldUser.setGroups(user.getGroups());
        mockDeleteUser(oldUser, true);
        mockCreateUser(user);
    }

    public void mockDeleteUser(User user, boolean userExistsInUserManagement) throws IOException, URISyntaxException {
        mockGetUser(user.getLogin(), userExistsInUserManagement);

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("user", user.getLogin(), "doDelete").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.FOUND));

        mockRemoveUserFromGroups(user.getGroups());
    }

    private void mockGetUser(String userLogin, boolean userExists) throws URISyntaxException, JsonProcessingException {
        var jenkinsUser = new JenkinsUserDTO();
        jenkinsUser.id = userLogin;

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("user", userLogin, "api", "json").build().toUri();
        if (userExists) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.FOUND).body(mapper.writeValueAsString(jenkinsUser)).contentType(MediaType.APPLICATION_JSON));
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        }
    }

    private void mockRemoveUserFromGroups(Set<String> groupsToRemove) throws IOException {
        if (groupsToRemove.isEmpty()) {
            return;
        }

        var exercises = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(groupsToRemove);
        for (ProgrammingExercise exercise : exercises) {
            var folderName = exercise.getProjectKey();
            mockRemovePermissionsFromUserOfFolder(folderName);
        }
    }

    private void mockRemovePermissionsFromUserOfFolder(String folderName) throws IOException {
        mockGetFolderConfig(folderName);
        doNothing().when(jenkinsServer).updateJob(eq(folderName), anyString(), eq(useCrumb));
    }

    public void mockCreateUser(User user) throws URISyntaxException, IOException {
        mockGetUser(user.getLogin(), false);

        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);
        doReturn(user.getPassword()).when(passwordService).decryptPassword(user);

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("securityRealm", "createAccountByAdmin").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.FOUND));

        mockAddUsersToGroups(user.getLogin(), user.getGroups());
    }

    private void mockAddUsersToGroups(String login, Set<String> groups) throws IOException {
        var exercises = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(groups);
        for (ProgrammingExercise exercise : exercises) {
            var jobName = exercise.getProjectKey();
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();

            if (groups.contains(course.getInstructorGroupName()) || groups.contains(course.getTeachingAssistantGroupName())) {
                // jenkinsJobPermissionsService.addPermissionsForUserToFolder
                mockGetFolderConfig(jobName);
                doNothing().when(jenkinsServer).updateJob(eq(jobName), anyString(), eq(useCrumb));
            }
        }
    }
}
