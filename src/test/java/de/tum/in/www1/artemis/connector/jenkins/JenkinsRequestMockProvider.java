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

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.model.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;

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

    public void mockCreateBuildPlan(String projectKey) throws IOException {
        JobWithDetails job = new JobWithDetails();
        job.setClient(jenkinsClient);
        // return null for the first call (when we check if the project exists) and the actual job for the 2nd, 3rd, 4th, ... call (when the jobs will be created)
        doReturn(null, job, job, job, job, job, job).when(jenkinsServer).getJob(anyString());
        doReturn(job).when(jenkinsServer).getJob(any(FolderJob.class), anyString());
        FolderJob folderJob = new FolderJob(projectKey, projectKey);
        doReturn(com.google.common.base.Optional.of(folderJob)).when(jenkinsServer).getFolderJob(job);
        doNothing().when(jenkinsServer).createJob(any(FolderJob.class), anyString(), anyString(), eq(useCrumb));
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
}
