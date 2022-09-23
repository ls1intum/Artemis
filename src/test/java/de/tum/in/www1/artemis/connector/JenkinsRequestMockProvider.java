package de.tum.in.www1.artemis.connector;

import static de.tum.in.www1.artemis.util.FileUtils.loadFileFromResources;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.client.HttpResponseException;
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
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.JenkinsUserDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.jobs.JenkinsJobPermissionsService;

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

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private AutoCloseable closeable;

    public JenkinsRequestMockProvider(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        // We remove JenkinsAuthorizationInterceptor because the tests hit the intercept() method
        // which has its' own instance of RestTemplate (in order to get a crumb(. Since that template
        // isn't mocked, it will throw an exception.
        // TODO: Find a way to either mock the interceptor or mock its RestTemplate
        this.restTemplate.setInterceptors(List.of());
        this.shortTimeoutRestTemplate.setInterceptors(List.of());
    }

    public void enableMockingOfRequests(JenkinsServer jenkinsServer) {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).bufferContent().build();
        shortTimeoutMockServer = MockRestServiceServer.bindTo(shortTimeoutRestTemplate).ignoreExpectOrder(true).bufferContent().build();
        this.jenkinsServer = jenkinsServer;
        closeable = MockitoAnnotations.openMocks(this);
    }

    public void reset() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        if (mockServer != null) {
            mockServer.reset();
        }
        if (shortTimeoutMockServer != null) {
            shortTimeoutMockServer.reset();
        }
    }

    /**
     * Verify that the mocked REST-calls were called
     */
    public void verifyMocks() {
        mockServer.verify();
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise, boolean shouldFail) throws IOException {
        // TODO: we need to mockRetrieveArtifacts folder(...)
        if (shouldFail) {
            doThrow(IOException.class).when(jenkinsServer).createFolder(null, exercise.getProjectKey(), useCrumb);
        }
        else {
            doNothing().when(jenkinsServer).createFolder(null, exercise.getProjectKey(), useCrumb);
        }
    }

    public void mockCreateBuildPlan(String projectKey, String planKey, boolean jobAlreadyExists) throws IOException {
        var jobFolder = projectKey;
        var job = jobFolder + "-" + planKey;
        mockCreateJobInFolder(jobFolder, job, jobAlreadyExists);
        mockGivePlanPermissions(jobFolder, job);
        mockTriggerBuild(jobFolder, job, false);
    }

    public void mockCreateJobInFolder(String jobFolder, String job, boolean jobAlreadyExists) throws IOException {
        var folderJob = new FolderJob();
        mockGetFolderJob(jobFolder, folderJob);
        if (jobAlreadyExists) {
            var jobWithDetails = new JobWithDetails();
            doReturn(jobWithDetails).when(jenkinsServer).getJob(any(FolderJob.class), eq(job));
        }
        else {
            doReturn(null).when(jenkinsServer).getJob(any(FolderJob.class), eq(job));
            doNothing().when(jenkinsServer).createJob(any(FolderJob.class), eq(job), anyString(), eq(useCrumb));
        }
    }

    public void mockGivePlanPermissions(String jobFolder, String job) throws IOException {
        mockGetJobConfig(jobFolder, job);
        mockUpdateJob(jobFolder, job);
        mockGetFolderConfig(jobFolder);
        doNothing().when(jenkinsServer).updateJob(eq(jobFolder), anyString(), eq(useCrumb));
    }

    private void mockGetJobConfig(String folderName, String jobName) throws IOException {
        doReturn(new JobWithDetails()).when(jenkinsServer).getJob(folderName);
        doReturn(Optional.of(new FolderJob())).when(jenkinsServer).getFolderJob(any(JobWithDetails.class));

        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");
        doReturn(mockXml).when(jenkinsServer).getJobXml(any(FolderJob.class), eq(jobName));
    }

    public void mockGetFolderConfig(String folderName) throws IOException {
        doReturn(new JobWithDetails()).when(jenkinsServer).getJob(folderName);
        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");
        doReturn(mockXml).when(jenkinsServer).getJobXml(eq(folderName));
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

    public void mockCheckIfProjectExists(ProgrammingExercise exercise, boolean exists, boolean shouldFail) throws IOException {
        var jobOrNull = exists ? mock(JobWithDetails.class) : null;
        if (jobOrNull != null) {
            doReturn("http://some-job-url.com/").when(jobOrNull).getUrl();
        }

        if (shouldFail) {
            doThrow(IOException.class).when(jenkinsServer).getJob(exercise.getProjectKey());
        }
        else {
            doReturn(jobOrNull).when(jenkinsServer).getJob(exercise.getProjectKey());
        }
    }

    public void mockCheckIfProjectExistsJobIsNull(ProgrammingExercise exercise) throws IOException {
        doReturn(null).when(jenkinsServer).getJob(exercise.getProjectKey());
    }

    public void mockCheckIfProjectExistsJobUrlEmptyOrNull(ProgrammingExercise exercise, boolean urlEmpty) throws IOException {
        var job = mock(JobWithDetails.class);
        doReturn(job).when(jenkinsServer).getJob(exercise.getProjectKey());
        doReturn(urlEmpty ? "" : null).when(job).getUrl();
    }

    public void mockCopyBuildPlan(String sourceProjectKey, String targetProjectKey) throws IOException {
        mockGetJobXmlForBuildPlanWith(sourceProjectKey, "<xml></xml>");
        mockSaveJobXml(targetProjectKey);
    }

    private void mockSaveJobXml(String targetProjectKey) throws IOException {
        mockGetFolderJob(targetProjectKey, new FolderJob());
        // copyBuildPlan uses #createJobInFolder()
        doReturn(null).when(jenkinsServer).getJob(any(), anyString());
        doNothing().when(jenkinsServer).createJob(any(), anyString(), anyString(), eq(useCrumb));
    }

    public void mockConfigureBuildPlan(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException {
        final var projectKey = exercise.getProjectKey();
        final var planKey = projectKey + "-" + getCleanPlanName(username.toUpperCase());
        mockUpdatePlanRepository(projectKey, planKey, true);
        mockEnablePlan(projectKey, planKey, true, false);
    }

    private String getCleanPlanName(String planName) {
        return planName.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public void mockUpdatePlanRepository(String projectKey, String planName, boolean useLegacyXml) throws IOException, URISyntaxException {
        var jobConfigXmlFilename = useLegacyXml ? "legacy-job-config.xml" : "job-config.xml";
        var mockXml = loadFileFromResources("test-data/jenkins-response/" + jobConfigXmlFilename);

        mockGetFolderJob(projectKey, new FolderJob());
        mockGetJobXmlForBuildPlanWith(projectKey, mockXml);

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("job", projectKey, "job", planName, "config.xml").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));

        mockTriggerBuild(projectKey, planName, false);
        mockTriggerBuild(projectKey, planName, false);
    }

    public void mockUpdatePlanRepository(String projectKey, String planName, HttpStatus expectedHttpStatus) throws IOException, URISyntaxException {
        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");

        mockGetFolderJob(projectKey, new FolderJob());
        mockGetJobXmlForBuildPlanWith(projectKey, mockXml);

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("job", projectKey, "job", planName, "config.xml").build().toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(expectedHttpStatus));
    }

    private void mockGetJobXmlForBuildPlanWith(String projectKey, String xmlToReturn) throws IOException {
        mockGetFolderJob(projectKey, new FolderJob());
        doReturn(xmlToReturn).when(jenkinsServer).getJobXml(any(), any());
    }

    public void mockEnablePlan(String projectKey, String planKey, boolean planExistsInCi, boolean shouldFail) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("job", projectKey, "job", planKey, "enable").build().toUri();
        if (shouldFail) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        }
        else {
            var status = planExistsInCi ? HttpStatus.FOUND : HttpStatus.NOT_FOUND;
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(status));
        }
    }

    public void mockCopyBuildPlanForParticipation(ProgrammingExercise exercise, String username) throws IOException {
        final var projectKey = exercise.getProjectKey();
        mockCopyBuildPlan(projectKey, projectKey);
    }

    public void mockGetJob(String projectKey, String jobName, JobWithDetails jobToReturn, boolean getJobFails) throws IOException {
        final var folder = new FolderJob();
        mockGetFolderJob(projectKey, folder);
        if (!getJobFails) {
            doReturn(jobToReturn).when(jenkinsServer).getJob(folder, jobName);
        }
        else {
            doThrow(IOException.class).when(jenkinsServer).getJob(folder, jobName);
        }
    }

    public void mockGetFolderJob(String folderName, FolderJob folderJobToReturn) throws IOException {
        final var jobWithDetails = new JobWithDetails();
        doReturn(jobWithDetails).when(jenkinsServer).getJob(folderName);
        doReturn(com.google.common.base.Optional.of(folderJobToReturn)).when(jenkinsServer).getFolderJob(jobWithDetails);
    }

    public BuildWithDetails mockGetLatestBuildLogs(ProgrammingExerciseStudentParticipation participation, boolean useLegacyLogs) throws IOException {
        String projectKey = participation.getProgrammingExercise().getProjectKey();
        String buildPlanId = participation.getBuildPlanId();

        final var job = mock(JobWithDetails.class);
        mockGetJob(projectKey, buildPlanId, job, false);

        final var build = mock(Build.class);
        doReturn(build).when(job).getLastBuild();

        final var buildWithDetails = mock(BuildWithDetails.class);
        doReturn(buildWithDetails).when(build).details();

        if (useLegacyLogs) {
            doReturn(null).when(buildWithDetails).getConsoleOutputText();
            String htmlString = loadFileFromResources("test-data/jenkins-response/legacy-failed-build-log.html");
            doReturn(htmlString).when(buildWithDetails).getConsoleOutputHtml();
        }
        else {
            File file = ResourceUtils.getFile("classpath:test-data/jenkins-response/failed-build-log.txt");
            try (var lines = Files.lines(file.toPath())) {
                String result = lines.collect(Collectors.joining("\n"));
                doReturn(result).when(buildWithDetails).getConsoleOutputText();
            }
        }
        return buildWithDetails;

    }

    public void mockGetLegacyBuildLogs(ProgrammingExerciseStudentParticipation participation) throws IOException {
        String projectKey = participation.getProgrammingExercise().getProjectKey();
        String buildPlanId = participation.getBuildPlanId();

        final var job = mock(JobWithDetails.class);
        mockGetJob(projectKey, buildPlanId, job, false);

        final var build = mock(Build.class);
        doReturn(build).when(job).getLastBuild();

        final var buildWithDetails = mock(BuildWithDetails.class);
        doReturn(buildWithDetails).when(build).details();

        String htmlString = loadFileFromResources("test-data/jenkins-response/legacy-failed-build-log.html");
        doReturn(htmlString).when(buildWithDetails).getConsoleOutputText();
        doReturn(htmlString).when(buildWithDetails).getConsoleOutputHtml();
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
        mockAddUsersToGroups(user.getLogin(), groupsToAdd, false);
    }

    private void mockUpdateUser(User user, boolean userExists) throws URISyntaxException, IOException {
        mockGetUser(user.getLogin(), userExists, false);
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

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("securityRealm", "createAccountByAdmin").build().toUri();
        var status = shouldFail ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.FOUND;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(status));

        mockAddUsersToGroups(user.getLogin(), user.getGroups(), false);
    }

    public void mockAddUsersToGroups(String login, Set<String> groups, boolean shouldfail) throws IOException {
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        for (ProgrammingExercise exercise : exercises) {
            var jobName = exercise.getProjectKey();
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();

            if (groups.contains(course.getInstructorGroupName()) || groups.contains(course.getEditorGroupName()) || groups.contains(course.getTeachingAssistantGroupName())) {
                mockGetFolderConfig(jobName);
                if (shouldfail) {
                    doThrow(IOException.class).when(jenkinsServer).updateJob(eq(jobName), anyString(), eq(useCrumb));
                }
                else {
                    doNothing().when(jenkinsServer).updateJob(eq(jobName), anyString(), eq(useCrumb));
                }
            }
        }
    }

    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup, boolean failToAddUsers,
            boolean failToRemoveUsers) throws IOException {
        var newInstructorGroup = updatedCourse.getInstructorGroupName();
        var newEditorGroup = updatedCourse.getEditorGroupName();
        var newTeachingAssistantGroup = updatedCourse.getTeachingAssistantGroupName();

        // Don't do anything if the groups didn't change
        if (newInstructorGroup.equals(oldInstructorGroup) && newEditorGroup.equals(oldEditorGroup) && newTeachingAssistantGroup.equals(oldTeachingAssistantGroup)) {
            return;
        }

        mockRemovePermissionsFromInstructorsAndEditorsAndTAsForCourse(updatedCourse, failToRemoveUsers);
        mockAssignPermissionsToInstructorAndEditorAndTAsForCourse(updatedCourse, failToAddUsers);
    }

    private void mockRemovePermissionsFromInstructorsAndEditorsAndTAsForCourse(Course course, boolean shouldFailToRemove) throws IOException {
        var exercises = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(course);
        for (var exercise : exercises) {
            if (shouldFailToRemove) {
                doThrow(IOException.class).when(jenkinsJobPermissionsService).removePermissionsFromUsersForFolder(any(), eq(exercise.getProjectKey()), any());
            }
            else {
                doNothing().when(jenkinsJobPermissionsService).removePermissionsFromUsersForFolder(any(), eq(exercise.getProjectKey()), any());
            }
        }
    }

    private void mockAssignPermissionsToInstructorAndEditorAndTAsForCourse(Course course, boolean shouldFailToAdd) throws IOException {
        var exercises = programmingExerciseRepository.findAllProgrammingExercisesInCourseOrInExamsOfCourse(course);
        for (var exercise : exercises) {
            var job = exercise.getProjectKey();
            mockAddInstructorAndEditorAndTAPermissionsToUsersForFolder(job, shouldFailToAdd);
        }
    }

    private void mockAddInstructorAndEditorAndTAPermissionsToUsersForFolder(String folderName, boolean shouldFailToAdd) throws IOException {
        mockGetFolderConfig(folderName);
        if (shouldFailToAdd) {
            doThrow(IOException.class).when(jenkinsServer).updateJob(eq(folderName), anyString(), eq(useCrumb));
        }
        else {
            doNothing().when(jenkinsServer).updateJob(eq(folderName), anyString(), eq(useCrumb));
        }
    }

    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws IOException {
        mockGetFolderJob(projectKey, new FolderJob());
        if (shouldFail) {
            doThrow(new HttpResponseException(400, "Bad Request")).when(jenkinsServer).deleteJob(any(FolderJob.class), eq(planName), eq(useCrumb));
        }
        else {
            doNothing().when(jenkinsServer).deleteJob(any(FolderJob.class), eq(planName), eq(useCrumb));
        }
    }

    public void mockDeleteBuildPlanNotFound(String projectKey, String planName) throws IOException {
        mockGetFolderJob(projectKey, new FolderJob());
        doThrow(new HttpResponseException(404, "Not found")).when(jenkinsServer).deleteJob(any(FolderJob.class), eq(planName), eq(useCrumb));
    }

    public void mockDeleteBuildPlanFailWithException(String projectKey, String planName) throws IOException {
        mockGetFolderJob(projectKey, new FolderJob());
        doThrow(new IOException("IOException")).when(jenkinsServer).deleteJob(any(FolderJob.class), eq(planName), eq(useCrumb));
    }

    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) throws IOException {
        if (shouldFail) {
            doThrow(new HttpResponseException(400, "Bad Request")).when(jenkinsServer).deleteJob(projectKey, useCrumb);
        }
        else {
            doNothing().when(jenkinsServer).deleteJob(projectKey, useCrumb);
        }
    }

    public void mockGetBuildStatus(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetLastBuild)
            throws IOException, URISyntaxException {
        if (!planExistsInCi) {
            mockGetJob(projectKey, planName, null, false);
            return;
        }

        var jobWithDetails = mock(JobWithDetails.class);
        mockGetJob(projectKey, planName, jobWithDetails, false);

        if (planIsActive && !planIsBuilding) {
            doReturn(true).when(jobWithDetails).isInQueue();
            return;
        }

        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("job", projectKey, "job", planName, "lastBuild", "api", "json").build().toUri();
        final var body = new ObjectMapper().writeValueAsString(Map.of("building", planIsBuilding && planIsActive));
        final var status = failToGetLastBuild ? HttpStatus.NOT_FOUND : HttpStatus.OK;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(status).body(body).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockHealth(boolean isRunning, HttpStatus httpStatus) throws URISyntaxException {
        final var uri = UriComponentsBuilder.fromUri(jenkinsServerUrl.toURI()).pathSegment("login").build().toUri();
        if (isRunning) {
            shortTimeoutMockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(httpStatus).body("lol"));
        }
        else {
            shortTimeoutMockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    public void mockCheckIfBuildPlanExists(String projectKey, String buildPlanId, boolean buildPlanExists, boolean shouldfail) throws IOException {
        var toReturn = buildPlanExists ? new JobWithDetails() : null;
        mockGetJob(projectKey, buildPlanId, toReturn, shouldfail);
    }

    public void mockTriggerBuild(String projectKey, String buildPlanId, boolean triggerBuildFails) throws IOException {
        var jobWithDetails = mock(JobWithDetails.class);
        mockGetJob(projectKey, buildPlanId, jobWithDetails, triggerBuildFails);
        if (!triggerBuildFails) {
            doReturn(new QueueReference("")).when(jobWithDetails).build();
        }
    }

    public void mockGivePlanPermissionsThrowException(String projectKey, String projectKey1) throws IOException {
        doThrow(IOException.class).when(jenkinsJobPermissionsService).addInstructorAndEditorAndTAPermissionsToUsersForJob(any(), any(), any(), eq(projectKey), eq(projectKey1));
    }
}
