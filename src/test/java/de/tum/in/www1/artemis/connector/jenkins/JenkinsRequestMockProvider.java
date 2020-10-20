package de.tum.in.www1.artemis.connector.jenkins;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.model.ExtractHeader;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.JobWithDetails;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Component
@Profile("jenkins")
public class JenkinsRequestMockProvider {

    private final RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @InjectMocks
    private JenkinsServer jenkinsServer;

    @Mock
    private JenkinsHttpClient jenkinsClient;

    public JenkinsRequestMockProvider(@Qualifier("jenkinsRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void enableMockingOfRequests(JenkinsServer jenkinsServer) {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        this.jenkinsServer = jenkinsServer;
        MockitoAnnotations.openMocks(this);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException {
        // TODO: we need to mockRetrieveArtifacts folder(...)
        doNothing().when(jenkinsServer).createFolder(null, exercise.getProjectKey(), true);
    }

    public void mockCreateBuildPlan(String projectKey) throws IOException {
        JobWithDetails job = new JobWithDetails();
        job.setClient(jenkinsClient);
        // return null for the first call (when we check if the project exists) and the actual job for the 2nd, 3rd, 4th, ... call (when the jobs will be created)
        doReturn(null, job, job, job, job, job, job).when(jenkinsServer).getJob(anyString());
        doReturn(job).when(jenkinsServer).getJob(any(FolderJob.class), anyString());
        FolderJob folderJob = new FolderJob(projectKey, projectKey);
        doReturn(com.google.common.base.Optional.of(folderJob)).when(jenkinsServer).getFolderJob(job);
        doNothing().when(jenkinsServer).createJob(any(FolderJob.class), anyString(), anyString(), anyBoolean());
    }

    public void mockTriggerBuild() throws IOException {
        ExtractHeader location = new ExtractHeader();
        location.setLocation("mockLocation");
        doReturn(location).when(jenkinsClient).post(anyString(), any(), any(), anyBoolean());
    }

}
