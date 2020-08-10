package de.tum.in.www1.artemis.connector.jenkins;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.google.common.base.Optional;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.model.ExtractHeader;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Component
@Profile("jenkins")
public class JenkinsRequestMockProvider {

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

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

    public void mockCheckIfProjectExists(ProgrammingExercise exercise, final boolean exists) throws IOException {
        final var projectKey = exercise.getProjectKey();

        FolderJob folderJob = new FolderJob(projectKey, projectKey);
        Job jobWithDetails = null;
        if (exists) {
            jobWithDetails = new JobWithDetails();
        }

        doReturn(jobWithDetails).when(jenkinsServer).getJob(any());
        doReturn(null).when(jenkinsServer).getFolderJob(null);
        doReturn(Optional.of(folderJob)).when(jenkinsServer).getFolderJob(jobWithDetails);
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException {
        doNothing().when(jenkinsServer).createFolder(null, exercise.getProjectKey(), true);
    }

    public void mockCreateBuildPlan() throws IOException {
        JobWithDetails job = new JobWithDetails();
        job.setClient(jenkinsClient);
        doReturn(job).when(jenkinsServer).getJob(any(FolderJob.class), anyString());
        doNothing().when(jenkinsServer).createJob(any(FolderJob.class), anyString(), anyString(), anyBoolean());
    }

    public void mockTriggerBuild() throws IOException {
        ExtractHeader location = new ExtractHeader();
        location.setLocation("mockLocation");
        doReturn(location).when(jenkinsClient).post(anyString(), any(), any(), anyBoolean());
    }

}
