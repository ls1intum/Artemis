package de.tum.cit.aet.artemis.core.connector;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;
import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.loadFileFromResources;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hamcrest.Matchers;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.service.jenkins.JenkinsEndpoints;
import de.tum.cit.aet.artemis.programming.service.jenkins.dto.JenkinsUserDTO;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobPermissionsService;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

@Component
@Profile(PROFILE_JENKINS)
public class JenkinsRequestMockProvider {

    @Value("${artemis.continuous-integration.url}")
    private URI jenkinsServerUri;

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    private final RestTemplate shortTimeoutRestTemplate;

    private MockRestServiceServer shortTimeoutMockServer;

    // will be assigned in enableMockingOfRequests(), can be used like a MockitoSpyBean
    private JenkinsJobPermissionsService jenkinsJobPermissionsService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private AutoCloseable closeable;

    public JenkinsRequestMockProvider(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate,
            @Qualifier("shortTimeoutJenkinsRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        // We remove JenkinsAuthorizationInterceptor because the tests hit the intercept() method
        // which has its' own instance of RestTemplate (in order to get a crumb(. Since that template
        // isn't mocked, it will throw an exception.
        this.restTemplate.setInterceptors(List.of());
        this.shortTimeoutRestTemplate.setInterceptors(List.of());
    }

    public void enableMockingOfRequests(JenkinsJobPermissionsService jenkinsJobPermissionsService) {
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).bufferContent().build();
        shortTimeoutMockServer = MockRestServiceServer.bindTo(shortTimeoutRestTemplate).ignoreExpectOrder(true).bufferContent().build();
        this.jenkinsJobPermissionsService = jenkinsJobPermissionsService;
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

    private String buildJobName(final String projectKey, final String planName) {
        // the build plan ID can be provided either as the full name already (contains -), or only the participation ID suffix.
        if (planName.contains("-")) {
            return planName;
        }
        else {
            return projectKey + "-" + planName;
        }
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise, boolean shouldFail) {
        //@formatter:off
        URI uri = JenkinsEndpoints.NEW_FOLDER.buildEndpoint(jenkinsServerUri)
            .queryParam("name", exercise.getProjectKey())
            .queryParam("mode", "com.cloudbees.hudson.plugins.folder.Folder")
            .queryParam("from", "")
            .queryParam("Submit", "OK")
            .build(true).toUri();
        //@formatter:on
        if (shouldFail) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest());
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        }
    }

    public void mockCreateBuildPlan(String projectKey, String planKey, boolean jobAlreadyExists) throws IOException {
        final String job = buildJobName(projectKey, planKey);

        mockCreateJobInFolder(projectKey, job, jobAlreadyExists);
        mockGivePlanPermissions(projectKey, job);
        mockTriggerBuild(projectKey, job, false);
    }

    public void mockCreateCustomBuildPlan(String projectKey, String planKey) throws IOException {
        final String job = buildJobName(projectKey, planKey);
        mockCreateBuildPlan(projectKey, job, false);
    }

    private void mockCreateJobInExistingFolder(String jobFolder, String job) throws IOException {
        mockGetFolderJob(jobFolder);
        mockGetJob(jobFolder, job, null, false);
        mockCreateJob(jobFolder, job);
    }

    public void mockCreateJobInFolder(String jobFolder, String job, boolean jobAlreadyExists) throws IOException {
        var folderJob = jobAlreadyExists ? new JenkinsJobService.FolderJob(jobFolder, "description", "url") : null;
        mockGetFolderJob(jobFolder, folderJob);
        if (jobAlreadyExists) {
            var jobWithDetails = new JenkinsJobService.JobWithDetails(job, "description", false);
            // NOTE: this method also invokes mockGetFolderJob(...)
            mockGetJob(jobFolder, job, jobWithDetails, false);
        }
        else {
            mockGetJob(jobFolder, job, null, false);
            mockCreateJob(jobFolder, job);
        }
    }

    public void mockCreateJob(String jobFolder, String job) {
        URI uri = JenkinsEndpoints.NEW_PLAN.buildEndpoint(jenkinsServerUri, jobFolder).queryParam("name", job).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
    }

    /**
     * Mock equivalent of {@link JenkinsJobPermissionsService#addInstructorAndEditorAndTAPermissionsToUsersForJob(Set, Set, Set, String, String)}.
     *
     * @param folderName The folder the job is in.
     * @param job        The name of the job itself.
     */
    public void mockGivePlanPermissions(String folderName, String job) throws IOException {
        // add permissions to job itself
        mockGetJobConfig(folderName, job);
        mockUpdatePlanRepository(folderName, job, false);

        // add read permission to folder the job is in
        mockAddInstructorAndEditorAndTAPermissionsToUsersForFolder(folderName, false);
    }

    public void mockGetJobConfig(String folderName, String jobName) throws IOException {
        mockGetFolderJob(folderName);
        mockGetJobConfigPlain(folderName, jobName);
    }

    /**
     * Should only be used when explicitly only the single request is needed. Use {@link #mockGetJobConfig(String, String)} otherwise.
     *
     * @param folderName The name of the folder.
     * @param jobName    The name of the build plan itself.
     */
    public void mockGetJobConfigPlain(String folderName, String jobName) throws IOException {
        URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(jenkinsServerUri, folderName, jobName).build(true).toUri();
        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(mockXml));
    }

    public void mockGetFolderConfig(String folderName) throws IOException {
        mockGetFolderJob(folderName);
        mockGetFolderConfigPlain(folderName);
    }

    /**
     * Should only be used when explicitly only the single GET request is needed. Use {@link #mockGetFolderConfig(String)} otherwise.
     *
     * @param folderName The name of the folder.
     * @throws IOException Required due to serialization, should never occur.
     */
    public void mockGetFolderConfigPlain(String folderName) throws IOException {
        URI uri = JenkinsEndpoints.FOLDER_CONFIG.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(mockXml).contentType(MediaType.APPLICATION_XML));
    }

    public void mockUpdateFolderConfigPlain(String folderName) throws IOException {
        URI uri = JenkinsEndpoints.FOLDER_CONFIG.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess().body(mockXml).contentType(MediaType.APPLICATION_XML));
    }

    public void mockCheckIfProjectExists(ProgrammingExercise exercise, boolean exists, boolean shouldFail) throws IOException {
        var projectKey = exercise.getProjectKey();
        URI uri = JenkinsEndpoints.GET_FOLDER_JOB.buildEndpoint(jenkinsServerUri, projectKey).build(true).toUri();
        var jobOrNull = exists ? new JenkinsJobService.FolderJob(projectKey, "description", "url") : null;
        var response = mapper.writeValueAsString(jobOrNull);

        if (shouldFail) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withBadRequest());
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(response));
        }
    }

    public void mockCheckIfProjectExistsJobIsNull(ProgrammingExercise exercise) throws IOException {
        mockGetFolderJob(exercise.getProjectKey(), null);
    }

    public void mockCopyBuildPlanFromTemplate(String sourceProjectKey, String targetProjectKey, String planKey) throws IOException {
        mockCopyBuildPlanFromPlanType(sourceProjectKey, targetProjectKey, planKey, TEMPLATE, false);
    }

    public void mockCopyBuildPlanFromTemplateIntoExistingTargetFolder(String sourceProjectKey, String targetProjectKey, String planKey) throws IOException {
        mockCopyBuildPlanFromPlanType(sourceProjectKey, targetProjectKey, planKey, TEMPLATE, true);
    }

    public void mockCopyBuildPlanFromSolution(String sourceProjectKey, String targetProjectKey, String planKey) throws IOException {
        mockCopyBuildPlanFromPlanType(sourceProjectKey, targetProjectKey, planKey, SOLUTION, false);
    }

    private void mockCopyBuildPlanFromPlanType(String sourceProjectKey, String targetProjectKey, String planKey, BuildPlanType planType, boolean folderExists) throws IOException {
        // the plan key has the form EXERCISE_ID-PARTICIPATION_ID
        final String sourcePlanKey = sourceProjectKey + "-" + planType.getName();
        mockGetJobXmlForBuildPlanWith(sourceProjectKey, sourcePlanKey, "<xml></xml>");
        mockSaveJobXml(targetProjectKey, planKey, folderExists);
    }

    private void mockSaveJobXml(String targetProjectKey, String planKey, boolean folderExists) throws IOException {
        mockGetFolderJob(targetProjectKey);
        mockGetJob(targetProjectKey, planKey, null, false);
        if (folderExists) {
            mockCreateJobInExistingFolder(targetProjectKey, planKey);
        }
        else {
            mockCreateBuildPlan(targetProjectKey, planKey, false);
        }
    }

    public void mockConfigureBuildPlan(ProgrammingExercise exercise, String username) throws IOException {
        final var projectKey = exercise.getProjectKey();
        final var planKey = projectKey + "-" + getCleanPlanName(username.toUpperCase());
        mockUpdatePlanRepository(projectKey, planKey, true);
        mockEnablePlan(projectKey, planKey, true, false);
    }

    private String getCleanPlanName(String planName) {
        return planName.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public void mockUpdatePlanRepository(String projectKey, String planName, boolean useLegacyXml) throws IOException {
        var jobConfigXmlFilename = useLegacyXml ? "legacy-job-config.xml" : "job-config.xml";
        var mockXml = loadFileFromResources("test-data/jenkins-response/" + jobConfigXmlFilename);

        mockGetFolderJob(projectKey);
        mockGetJobXmlForBuildPlanWith(projectKey, planName, mockXml);

        URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        // build plan URL is updated after the repository URIs, so in this case, the URI is used twice
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());

        mockTriggerBuild(projectKey, planName, false);
        mockTriggerBuild(projectKey, planName, false);
    }

    public void mockUpdatePlanConfigPlain(String projectKey, String planName) {
        URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
    }

    public void mockUpdatePlanRepository(String projectKey, String planName, HttpStatus expectedHttpStatus) throws IOException {
        var mockXml = loadFileFromResources("test-data/jenkins-response/job-config.xml");

        mockGetFolderJob(projectKey);
        mockGetJobXmlForBuildPlanWith(projectKey, planName, mockXml);

        URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(expectedHttpStatus));
    }

    private void mockGetJobXmlForBuildPlanWith(String projectKey, String planName, String xmlToReturn) throws IOException {
        mockGetFolderJob(projectKey);
        URI uri = JenkinsEndpoints.PLAN_CONFIG.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(xmlToReturn));
    }

    public void mockEnablePlan(String projectKey, String planKey, boolean planExistsInCi, boolean shouldFail) {
        URI uri = JenkinsEndpoints.ENABLE.buildEndpoint(jenkinsServerUri, projectKey, planKey).build(true).toUri();
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
        final var planKey = projectKey + "-" + getCleanPlanName(username.toUpperCase());
        mockCopyBuildPlanFromTemplateIntoExistingTargetFolder(projectKey, projectKey, planKey);
    }

    public void mockGetJob(String projectKey, String jobName, JenkinsJobService.JobWithDetails jobToReturn, boolean shouldFail) throws IOException {
        final var folder = new JenkinsJobService.FolderJob(projectKey, "description", "url");
        mockGetFolderJob(projectKey, folder);
        URI uri = JenkinsEndpoints.GET_JOB.buildEndpoint(jenkinsServerUri, projectKey, jobName).build(true).toUri();
        if (!shouldFail) {
            var response = mapper.writeValueAsString(jobToReturn);
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(response).contentType(MediaType.APPLICATION_JSON));
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withBadRequest());
        }
    }

    /**
     * Should only be used when explicitly only the single request is needed. Use {@link #mockGetJob(String, String, JenkinsJobService.JobWithDetails, boolean)} otherwise.
     *
     * @param projectKey  The name of the folder.
     * @param jobName     The name of the build plan itself.
     * @param jobToReturn The job that is returned by the mocked API.
     */
    public void mockGetJobPlain(String projectKey, String jobName, JenkinsJobService.JobWithDetails jobToReturn) throws IOException {
        URI uri = JenkinsEndpoints.GET_JOB.buildEndpoint(jenkinsServerUri, projectKey, jobName).build(true).toUri();
        var response = mapper.writeValueAsString(jobToReturn);
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(response).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockGetFolderJob(String folderName) throws IOException {
        mockGetFolderJob(folderName, new JenkinsJobService.FolderJob(folderName, "description", "url"));
    }

    public void mockGetFolderJob(String folderName, JenkinsJobService.FolderJob folderJobToReturn) throws IOException {
        URI uri = JenkinsEndpoints.GET_FOLDER_JOB.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
        var response = mapper.writeValueAsString(folderJobToReturn);
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess().body(response).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockUpdateUserAndGroups(String oldLogin, User user, Set<String> groupsToAdd, Set<String> groupsToRemove, boolean userExistsInJenkins) throws IOException {
        if (!oldLogin.equals(user.getLogin())) {
            mockUpdateUserLogin(oldLogin, user);
        }
        else {
            mockUpdateUser(user, userExistsInJenkins);
        }
        mockRemoveUserFromGroups(groupsToRemove, false);
        mockAddUsersToGroups(groupsToAdd, false);
    }

    private void mockUpdateUser(User user, boolean userExists) throws IOException {
        mockGetUser(user.getLogin(), userExists, false);
        mockDeleteUser(user, userExists, false);
        mockCreateUser(user, false, false, false);
    }

    private void mockUpdateUserLogin(String oldLogin, User user) throws IOException {
        if (oldLogin.equals(user.getLogin())) {
            return;
        }

        var oldUser = new User();
        oldUser.setLogin(oldLogin);
        oldUser.setGroups(user.getGroups());
        mockDeleteUser(oldUser, true, false);
        mockCreateUser(user, false, false, false);
    }

    public void mockDeleteUser(User user, boolean userExistsInUserManagement, boolean shouldFailToDelete) throws IOException {
        mockGetUser(user.getLogin(), userExistsInUserManagement, false);

        URI uri = JenkinsEndpoints.DELETE_USER.buildEndpoint(jenkinsServerUri, user.getLogin()).build(true).toUri();
        var status = shouldFailToDelete ? HttpStatus.NOT_FOUND : HttpStatus.FOUND;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(status));

        mockRemoveUserFromGroups(user.getGroups(), false);
    }

    private void mockGetUser(String userLogin, boolean userExists, boolean shouldFailToGetUser) throws JsonProcessingException {
        var jenkinsUser = new JenkinsUserDTO(userLogin, null, null);

        URI uri = JenkinsEndpoints.GET_USER.buildEndpoint(jenkinsServerUri, userLogin).build(true).toUri();
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

    public void mockGetAnyUser(boolean shouldFail, int requestCount) {
        final var httpStatus = shouldFail ? HttpStatus.NOT_FOUND : HttpStatus.FOUND;
        mockServer.expect(ExpectedCount.times(requestCount), requestTo(Matchers.endsWith("api/json"))).andRespond(withStatus(httpStatus));
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

    public void mockCreateUser(User user, boolean userExistsInCi, boolean shouldFail, boolean shouldFailToGetUser) throws IOException {
        mockGetUser(user.getLogin(), userExistsInCi, shouldFailToGetUser);

        URI uri = JenkinsEndpoints.CREATE_USER.buildEndpoint(jenkinsServerUri).build(true).toUri();
        var status = shouldFail ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.FOUND;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(status));

        mockAddUsersToGroups(user.getGroups(), false);
    }

    public void mockAddUsersToGroups(Set<String> groups, boolean shouldFail) throws IOException {
        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(groups);
        for (ProgrammingExercise exercise : exercises) {
            var folderName = exercise.getProjectKey();
            var course = exercise.getCourseViaExerciseGroupOrCourseMember();

            if (groups.contains(course.getInstructorGroupName()) || groups.contains(course.getEditorGroupName()) || groups.contains(course.getTeachingAssistantGroupName())) {
                mockGetFolderConfig(folderName);
                URI uri = JenkinsEndpoints.FOLDER_CONFIG.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();

                if (shouldFail) {
                    // updateJob
                    mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_XML));
                }
                else {
                    // updateJob
                    mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess().contentType(MediaType.APPLICATION_XML));
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
        URI uri = JenkinsEndpoints.FOLDER_CONFIG.buildEndpoint(jenkinsServerUri, folderName).build(true).toUri();
        if (shouldFailToAdd) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest());
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        }
    }

    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws IOException {
        mockGetFolderJob(projectKey);
        URI uri = JenkinsEndpoints.DELETE_JOB.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        if (shouldFail) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest());
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        }
    }

    /**
     * Should only be used when explicitly only the single POST request is needed. Use {@link #mockDeleteBuildPlan(String, String, boolean)} otherwise.
     *
     * @param projectKey The name of the folder.
     * @param planName   The name of the build plan itself.
     */
    public void mockDeleteBuildPlanPlain(String projectKey, String planName) {
        URI uri = JenkinsEndpoints.DELETE_JOB.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
    }

    public void mockDeleteBuildPlanNotFound(String projectKey, String planName) throws IOException {
        mockGetFolderJob(projectKey);
        URI uri = JenkinsEndpoints.DELETE_JOB.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withResourceNotFound());
    }

    public void mockDeleteBuildPlanFailWithException(String projectKey, String planName) throws IOException {
        mockGetFolderJob(projectKey);
        URI uri = JenkinsEndpoints.DELETE_JOB.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest());
    }

    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) throws IOException {
        URI uri = JenkinsEndpoints.DELETE_FOLDER.buildEndpoint(jenkinsServerUri, projectKey).build(true).toUri();
        if (shouldFail) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withBadRequest());
        }
        else {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        }
    }

    public void mockGetBuildStatus(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetLastBuild)
            throws IOException {
        if (!planExistsInCi) {
            mockGetJob(projectKey, planName, null, false);
            return;
        }

        boolean isQueued = planIsActive && !planIsBuilding;
        var jobWithDetails = new JenkinsJobService.JobWithDetails(planName, "", isQueued);
        mockGetJob(projectKey, planName, jobWithDetails, false);

        if (isQueued) {
            return;
        }

        URI uri = JenkinsEndpoints.LAST_BUILD.buildEndpoint(jenkinsServerUri, projectKey, planName).build(true).toUri();
        final var body = new ObjectMapper().writeValueAsString(Map.of("building", planIsBuilding && planIsActive));
        final var status = failToGetLastBuild ? HttpStatus.NOT_FOUND : HttpStatus.OK;
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(status).body(body).contentType(MediaType.APPLICATION_JSON));
    }

    public void mockHealth(boolean isRunning, HttpStatus httpStatus) {

        URI uri = JenkinsEndpoints.HEALTH.buildEndpoint(jenkinsServerUri).build(true).toUri();
        if (isRunning) {
            shortTimeoutMockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(httpStatus).body("lol"));
        }
        else {
            shortTimeoutMockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    public void mockCheckIfBuildPlanExists(String projectKey, String buildPlanId, boolean buildPlanExists, boolean shouldFail) throws IOException {
        var toReturn = buildPlanExists ? new JenkinsJobService.JobWithDetails(buildPlanId, "description", false) : null;
        mockGetJob(projectKey, buildPlanId, toReturn, shouldFail);
    }

    public void mockTriggerBuild(String projectKey, String buildPlanId, boolean triggerBuildFails) throws IOException {
        mockGetJob(projectKey, buildPlanId, new JenkinsJobService.JobWithDetails(buildPlanId, "description", false), triggerBuildFails);
        URI uri = JenkinsEndpoints.TRIGGER_BUILD.buildEndpoint(jenkinsServerUri, projectKey, buildPlanId).build(true).toUri();

        if (!triggerBuildFails) {
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
        }
        else {
            // simulate a client exception, because this is caught in the actual production code
            mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        }
    }

    /**
     * Should only be used when explicitly only the single request is needed. Use {@link #mockTriggerBuild(String, String, boolean)} otherwise.
     *
     * @param projectKey  The name of the folder.
     * @param buildPlanId The name of the build plan itself.
     */
    public void mockTriggerBuildPlain(String projectKey, String buildPlanId) {
        URI uri = JenkinsEndpoints.TRIGGER_BUILD.buildEndpoint(jenkinsServerUri, projectKey, buildPlanId).build(true).toUri();
        mockServer.expect(requestTo(uri)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess());
    }

    public void mockGivePlanPermissionsThrowException(String projectKey, String projectKey1) throws IOException {
        doThrow(IOException.class).when(jenkinsJobPermissionsService).addInstructorAndEditorAndTAPermissionsToUsersForJob(any(), any(), any(), eq(projectKey), eq(projectKey1));
    }
}
