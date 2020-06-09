package de.tum.in.www1.artemis.connector.jenkins;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;;

import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsException;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
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
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;

import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import de.tum.in.www1.artemis.service.util.XmlFileUtils;

@Component
@Profile("jenkins")
public class JenkinsRequestMockProvider {

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

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
        MockitoAnnotations.initMocks(this);
    }

    public void reset() {
        mockServer.reset();
    }

    public void mockCheckIfProjectExists(ProgrammingExercise exercise, final boolean exists) throws IOException {
        final var projectKey = exercise.getProjectKey();
        Job jobWithDetails = mockGetJob(exercise, exists);
        doReturn(Optional.of(new FolderJob(projectKey, projectKey))).when(jenkinsServer).getFolderJob(jobWithDetails);
    }

    public void mockCreateProjectForExercise(ProgrammingExercise exercise) throws IOException {
        doNothing().when(jenkinsServer).createFolder(null, exercise.getProjectKey(), true);
    }

    public void mockCreateBuildPlan(ProgrammingExercise exercise) throws IOException {
        final String projectKey = exercise.getProjectKey();
        final JobWithDetails projectJob = mockGetJob(exercise, true);
        final FolderJob project = new FolderJob(projectKey, projectKey);
        doReturn(Optional.of(project)).when(jenkinsServer).getFolderJob(projectJob);

        doNothing().when(jenkinsServer).createJob(any(FolderJob.class), anyString(), anyString(), anyBoolean());

        final JobWithDetails mockBuildPlanJob = new JobWithDetails();
        mockBuildPlanJob.setClient(jenkinsClient);
        final String basePlanKey = projectKey + "-BASE";
        final String solutionPlanKey = projectKey + "-SOLUTION";
        doReturn(mockBuildPlanJob).when(jenkinsServer).getJob(project, basePlanKey);
        doReturn(mockBuildPlanJob).when(jenkinsServer).getJob(project, solutionPlanKey);

    }

    public void mockTriggerBuild() throws IOException {
        ExtractHeader location = new ExtractHeader();
        location.setLocation("mockLocation");
        doReturn(location).when(jenkinsClient).post(anyString(), any(), any(), anyBoolean());
    }

    public void mockCopyBuildPlanForParticipation(ProgrammingExercise exercise, String username) throws IOException {
        final var projectKey = exercise.getProjectKey();
        final String buildplan = Files.readString(Path.of("src","test",  "resources", "templates", "jenkins", "mockBuildplan.xml"), StandardCharsets.UTF_8);
        final var sourcePlanKey = projectKey + "-" + BuildPlanType.TEMPLATE.getName();
        final var jobWithDetails = mockGetJob(exercise, true);
        final var folder = new FolderJob(exercise.getProjectKey(), exercise.getProjectKey());
        doReturn(Optional.of(folder)).when(jenkinsServer).getFolderJob(jobWithDetails);

        doReturn(buildplan).when(jenkinsServer).getJobXml(folder, sourcePlanKey);
        doNothing().when(jenkinsServer).createJob(any(FolderJob.class), anyString(), anyString(), anyBoolean());
    }

    private JobWithDetails mockGetJob(ProgrammingExercise exercise, boolean exists) throws IOException {
        JobWithDetails job = null;
        if (exists) {
            job = new JobWithDetails();
            job.setClient(jenkinsClient);
        }
        doReturn(job).when(jenkinsServer).getJob(exercise.getProjectKey());
        return job;
    }

    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException {
        final var planKey = exercise.getProjectKey() + "-" + username.toUpperCase();

        final String buildplan = Files.readString(Path.of("src","test",  "resources", "templates", "jenkins", "mockBuildplan.xml"), StandardCharsets.UTF_8);

        final var jobWithDetails = mockGetJob(exercise, true);
        final var folder = new FolderJob(exercise.getProjectKey(), exercise.getProjectKey());
        doReturn(Optional.of(folder)).when(jenkinsServer).getFolderJob(jobWithDetails);

        doReturn(buildplan).when(jenkinsServer).getJobXml(folder, planKey);
        Document xmlBuildPlan = XmlFileUtils.readFromString(buildplan);

        final String repoUrl = GITLAB_SERVER_URL.toString() + "/" + exercise.getProjectKey() + "/" + exercise.getProjectKey().toLowerCase() + "-" + username + ".git";
        final var urlElements = xmlBuildPlan.getElementsByTagName("url");
        urlElements.item(1).getFirstChild().setNodeValue(repoUrl);

        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        final var entity = new HttpEntity<>(writeXmlToString(xmlBuildPlan), headers);

        final var url = UriComponentsBuilder.fromUri(JENKINS_SERVER_URL.toURI()).path("job").pathSegment(exercise.getProjectKey()).pathSegment("job").pathSegment(planKey).pathSegment("config.xml").build(true).toUri();
        mockServer.expect(requestTo(url)).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.OK));
    }

    private String writeXmlToString(Document doc) {
        try {
            final var tf = TransformerFactory.newInstance();
            final var transformer = tf.newTransformer();
            final var writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));

            return writer.getBuffer().toString();
        }
        catch (TransformerException e) {
            final var errorMessage = "Unable to parse XML document to String! " + doc;
            throw new JenkinsException(errorMessage, e);
        }
    }

}
