package de.tum.in.www1.artemis.service.connectors;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.TextAssessmentQueueService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;

public class AtheneServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    TextAssessmentQueueService textAssessmentQueueService;

    @Mock
    TextExerciseRepository textExerciseRepository;

    @Mock
    TextBlockRepository textBlockRepository;

    @Mock
    TextSubmissionService textSubmissionService;

    @Mock
    TextClusterRepository textClusterRepository;

    private static final String SUBMIT_API_ENDPOINT = "http://localhost/submit";

    AtheneService atheneService;

    TextExercise exercise1;

    /**
     * Initializes atheneService and example exercise
     */
    @BeforeEach
    public void init() {
        // Create atheneService and inject @Value fields
        atheneService = new AtheneService(textSubmissionService, textBlockRepository, textClusterRepository, textExerciseRepository, textAssessmentQueueService, restTemplate);
        ReflectionTestUtils.setField(atheneService, "artemisServerUrl", artemisServerUrl);
        ReflectionTestUtils.setField(atheneService, "submitApiEndpoint", SUBMIT_API_ENDPOINT);

        // Create example exercise
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(1L, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        course1.setRegistrationEnabled(true);
        exercise1 = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureTimestamp, course1);
        exercise1.setId(1L);

        when(textExerciseRepository.findById(exercise1.getId())).thenReturn(Optional.ofNullable(exercise1));
    }

    /**
     * Submits a job to atheneService without any submissions
     */
    @Test
    public void submitJobWithoutSubmissions() {
        atheneService.submitJob(exercise1);
        assertThat(!atheneService.isTaskRunning(exercise1.getId()));
    }

    /**
     * Submits a job to atheneService with less than 10 submissions (will use fallback segmentation without athene)
     */
    @Test
    public void submitJobWithLessThan10Submissions() {
        // Let textSubmissionService return 9 generated submissions
        when(textSubmissionService.getTextSubmissionsWithTextBlocksByExerciseId(exercise1.getId())).thenReturn(ModelFactory.generateTextSubmissions(9));

        atheneService.submitJob(exercise1);
        assertThat(atheneService.isTaskRunning(exercise1.getId()));
    }

    /**
     * Submits a job to atheneService with 10 submissions (will trigger athene)
     */
    @Test
    public void submitJobWith10Submissions() {
        // Let textSubmissionService return 10 generated submissions

        when(textSubmissionService.getTextSubmissionsWithTextBlocksByExerciseIdAndLanguage(exercise1.getId(), Language.ENGLISH))
                .thenReturn(ModelFactory.generateTextSubmissions(10));

        // Create mock server
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        mockServer.expect(ExpectedCount.once(), requestTo(SUBMIT_API_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{ \"detail\": \"Submission successful\" }", MediaType.APPLICATION_JSON));

        atheneService.submitJob(exercise1);
        assertThat(atheneService.isTaskRunning(exercise1.getId()));

        // Check if mock server received specified requests
        mockServer.verify();
    }

    /**
     * Tests parseTextBlock of atheneService
     */
    @Test
    public void parseTextBlocks() {
        // Let textSubmissionService return 10 generated submissions
        when(textSubmissionService.getTextSubmissionsWithTextBlocksByExerciseId(exercise1.getId())).thenReturn(ModelFactory.generateTextSubmissions(10));

        List<AtheneDTO.TextBlockDTO> blocks = generateTextBlocks(10);
        List<TextBlock> textBlocks = atheneService.parseTextBlocks(blocks, exercise1.getId());
        for (TextBlock t : textBlocks) {
            assertThat(t.getId()).isNotNull();
            assertThat(t.getText()).isNotNull();
            assertThat(t.getType()).isEqualTo(TextBlockType.AUTOMATIC);
            assertThat(t.getSubmission()).isNotNull();
        }
        assertThat(textBlocks, hasSize(10));
    }

    /**
     * Tests processResult of atheneService
     */
    @Test
    public void processResult() {
        // Inject running task into atheneService
        List<Long> runningAtheneTasks = new ArrayList<>();
        runningAtheneTasks.add(exercise1.getId());
        ReflectionTestUtils.setField(atheneService, "runningAtheneTasks", runningAtheneTasks);
        // Verify injection
        assertThat(atheneService.isTaskRunning(exercise1.getId()));

        // Let textSubmissionService return 10 generated submissions
        when(textSubmissionService.getTextSubmissionsWithTextBlocksByExerciseId(exercise1.getId())).thenReturn(ModelFactory.generateTextSubmissions(10));

        // generate required parameters
        List<AtheneDTO.TextBlockDTO> blocks = generateTextBlocks(10);
        Map<Integer, TextCluster> clusters = generateClusters();

        // Catch call of atheneService to the textBlockRepository
        when(textBlockRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<TextBlock> set = invocation.getArgument(0);
            // Check for correct number of textBlocks
            if (set instanceof LinkedList) {
                assertThat(set, hasSize(10));
            }
            else if (set instanceof ArrayList) {
                assertThat(set, hasSize(0));
            }
            return set;
        });

        // Catch call of atheneService to the textClusterRepository
        when(textClusterRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            Collection<TextCluster> clusterCollection = invocation.getArgument(0);
            // Check for correct number of clusters
            assertThat(clusterCollection, hasSize(clusters.size()));
            return new ArrayList<>(clusterCollection);
        });

        // Call test method
        atheneService.processResult(clusters, blocks, exercise1.getId());
        assertThat(!atheneService.isTaskRunning(exercise1.getId()));
    }

    /**
     * Generates example AtheneDTO TextBlocks
     * @param count How many blocks should be generated
     * @return A list containing the generated TextBlocks
     */
    private List<AtheneDTO.TextBlockDTO> generateTextBlocks(int count) {
        List<AtheneDTO.TextBlockDTO> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AtheneDTO.TextBlockDTO newBlock = new AtheneDTO.TextBlockDTO();
            newBlock.setSubmissionId(i);
            newBlock.setStartIndex(0);
            newBlock.setEndIndex(30);
            newBlock.setText("This is an example text");
            // Calculate realistic hash (also see TextBlock.computeId())
            final String idString = newBlock.getSubmissionId() + ";" + newBlock.getStartIndex() + "-" + newBlock.getEndIndex() + ";" + newBlock.getText();
            newBlock.setId(sha1Hex(idString));
            blocks.add(newBlock);
        }

        return blocks;
    }

    /**
     * Generates example TextClusters
     * @return A Map with the generated TextClusters
     */
    private Map<Integer, TextCluster> generateClusters() {
        Map<Integer, TextCluster> clusters = new HashMap<>();
        TextCluster c1 = new TextCluster();
        clusters.put(0, c1);
        return clusters;
    }

}
