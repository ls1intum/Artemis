package de.tum.in.www1.artemis.service.connectors;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.athene.AtheneRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;

public class AtheneServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AtheneRequestMockProvider atheneRequestMockProvider;

    @Autowired
    private StudentParticipationRepository participationRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private AtheneService atheneService;

    private TextExercise exercise1;

    /**
     * Initializes atheneService and example exercise
     */
    @BeforeEach
    public void init() {
        // Create example exercise
        database.addUsers(10, 1, 1);
        var course = database.addCourseWithOneReleasedTextExercise();
        exercise1 = (TextExercise) course.getExercises().iterator().next();
        atheneRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() {
        atheneRequestMockProvider.reset();
        atheneService.finishTask(exercise1.getId());
        database.resetDatabase();
    }

    /**
     * Submits a job to atheneService without any submissions
     */
    @Test
    public void submitJobWithoutSubmissions() {
        atheneService.submitJob(exercise1);
        assertThat(!atheneService.isTaskRunning(exercise1.getId())).isTrue();
    }

    private List<TextSubmission> generateTextSubmissions(int size) {
        var textSubmissions = ModelFactory.generateTextSubmissions(size);
        for (var i = 0; i < size; i++) {
            var textSubmission = textSubmissions.get(i);
            var student = database.getUserByLogin("student" + (i + 1));
            var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise1, student);
            participation = participationRepository.save(participation);
            textSubmission.setParticipation(participation);
            textSubmission.setSubmitted(true);
        }

        return textSubmissionRepository.saveAll(textSubmissions);
    }

    /**
     * Submits a job to atheneService with less than 10 submissions (will use fallback segmentation without athene)
     */
    @Test
    public void submitJobWithLessThan10Submissions() {
        generateTextSubmissions(9);
        atheneService.submitJob(exercise1);
        assertThat(atheneService.isTaskRunning(exercise1.getId())).isFalse();
    }

    /**
     * Submits a job to atheneService with 10 submissions (will trigger athene)
     */
    @Test
    public void submitJobWith10Submissions() {
        generateTextSubmissions(10);

        // Create mock server
        atheneRequestMockProvider.mockSubmitSubmissions();

        atheneService.submitJob(exercise1);
        assertThat(atheneService.isTaskRunning(exercise1.getId())).isTrue();
    }

    /**
     * Tests parseTextBlock of atheneService
     */
    @Test
    public void parseTextBlocks() {
        int size = 10;
        var textSubmissions = generateTextSubmissions(size);

        List<AtheneDTO.TextBlockDTO> blocks = generateTextBlocks(textSubmissions);
        List<TextBlock> textBlocks = atheneService.parseTextBlocks(blocks, exercise1.getId());
        for (TextBlock textBlock : textBlocks) {
            assertThat(textBlock.getId()).isNotNull();
            assertThat(textBlock.getText()).isNotNull();
            assertThat(textBlock.getType()).isEqualTo(TextBlockType.AUTOMATIC);
            assertThat(textBlock.getSubmission()).isNotNull();
        }
        assertThat(textBlocks, hasSize(size));
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
        assertThat(atheneService.isTaskRunning(exercise1.getId())).isTrue();

        int size = 10;
        var textSubmissions = generateTextSubmissions(size);

        // generate required parameters
        List<AtheneDTO.TextBlockDTO> blocks = generateTextBlocks(textSubmissions);
        Map<Integer, TextCluster> clusters = generateClusters();

        // Call test method
        atheneService.processResult(clusters, blocks, exercise1.getId());
        assertThat(!atheneService.isTaskRunning(exercise1.getId())).isTrue();

        assertThat(textBlockRepository.findAll()).hasSize(size);
        assertThat(textClusterRepository.findAll()).hasSize(clusters.size());
    }

    /**
     * Generates example AtheneDTO TextBlocks
     * @param textSubmissions How many blocks should be generated
     * @return A list containing the generated TextBlocks
     */
    private List<AtheneDTO.TextBlockDTO> generateTextBlocks(List<TextSubmission> textSubmissions) {
        List<AtheneDTO.TextBlockDTO> blocks = new ArrayList<>();
        for (var textSubmission : textSubmissions) {
            AtheneDTO.TextBlockDTO newBlock = new AtheneDTO.TextBlockDTO();
            newBlock.setSubmissionId(textSubmission.getId());
            newBlock.setStartIndex(0);
            newBlock.setEndIndex(30);
            newBlock.setText(textSubmission.getText().substring(0, 30));
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
        TextCluster textCluster = new TextCluster();
        clusters.put(0, textCluster);
        return clusters;
    }

}
