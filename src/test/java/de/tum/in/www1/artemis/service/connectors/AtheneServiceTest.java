package de.tum.in.www1.artemis.service.connectors;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
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
import de.tum.in.www1.artemis.service.TextBlockService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;

public class AtheneServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    AtheneService atheneService;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    TextBlockService textBlockService;

    @Mock
    TextExerciseRepository textExerciseRepository;

    @Mock
    TextBlockRepository textBlockRepository;

    @Mock
    TextSubmissionService textSubmissionService;

    @Mock
    TextClusterRepository textClusterRepository;

    @Autowired
    TextAssessmentQueueService textAssessmentQueueService;

    TextExercise exercise1;

    private final String API_ENDPOINT = "http://localhost/submit";

    @BeforeEach
    public void init() {
        atheneService = new AtheneService(textSubmissionService, textBlockRepository, textBlockService, textClusterRepository, textExerciseRepository, textAssessmentQueueService);

        // Create example exercise
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(1L, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "instructor");
        course1.setRegistrationEnabled(true);
        exercise1 = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureTimestamp, course1);
        exercise1.setId(1L);

        // Inject @Value fields to atheneService
        ReflectionTestUtils.setField(atheneService, "ARTEMIS_SERVER_URL", ARTEMIS_SERVER_URL);
        ReflectionTestUtils.setField(atheneService, "API_ENDPOINT", API_ENDPOINT);
        String API_SECRET = "YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=";
        ReflectionTestUtils.setField(atheneService, "API_SECRET", API_SECRET);

        when(textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesById(exercise1.getId())).thenReturn(Optional.ofNullable(exercise1));
    }

    @Test
    public void submitJobWithoutSubmissions() {
        // Catch call of atheneService to the textBlockRepository
        when(textBlockRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            ArrayList<TextBlock> set = invocation.getArgument(0);
            // Check for correct number of textBlocks
            assert (set.size() == 0);
            return set;
        });

        atheneService.submitJob(exercise1);
        assert (!atheneService.isTaskRunning(exercise1.getId()));
    }

    @Test
    public void submitJobWithLessThan10Submissions() {
        // Let textSubmissionService return 9 generated submissions
        when(textSubmissionService.getTextSubmissionsByExerciseId(exercise1.getId(), true, false)).thenReturn(generateSubmissions(9));

        // Catch call of atheneService to the textBlockRepository
        when(textBlockRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            ArrayList<TextBlock> set = invocation.getArgument(0);
            // Check for correct number of textBlocks
            assert (set.size() == 32);
            return set;
        });

        atheneService.submitJob(exercise1);
        assert (!atheneService.isTaskRunning(exercise1.getId()));
    }

    @Test
    public void submitJobWith10Submissions() {
        // Let textSubmissionService return 10 generated submissions
        when(textSubmissionService.getTextSubmissionsByExerciseId(exercise1.getId(), true, false)).thenReturn(generateSubmissions(10));

        // Inject restTemplate to connector of atheneService
        RemoteArtemisServiceConnector conn = (RemoteArtemisServiceConnector) ReflectionTestUtils.getField(atheneService, "connector");
        assert (conn != null);
        ReflectionTestUtils.setField(conn, "restTemplate", restTemplate);

        // Create mock server
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        mockServer.expect(ExpectedCount.once(), requestTo(API_ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{ \"detail\": \"Submission successful\" }", MediaType.APPLICATION_JSON));

        atheneService.submitJob(exercise1);
        assert (atheneService.isTaskRunning(exercise1.getId()));

        // Check if mock server received specified requests
        mockServer.verify();
    }

    @Test
    public void parseTextBlocks() {
        // Let textSubmissionService return 10 generated submissions
        when(textSubmissionService.getTextSubmissionsByExerciseId(exercise1.getId(), true, false)).thenReturn(generateSubmissions(10));
        List<AtheneDTO.TextBlock> blocks = generateTextBlocks(10);
        List<TextBlock> textBlocks = atheneService.parseTextBlocks(blocks, exercise1.getId());
        for (TextBlock t : textBlocks) {
            assert (t.getId() != null);
            assert (t.getText() != null);
            assert (t.getType() == TextBlockType.AUTOMATIC);
            assert (t.getSubmission() != null);
        }
        assert (textBlocks.size() == 10);
    }

    @Test
    public void processResult() {
        // Inject running task into atheneService
        List<Long> runningAtheneTasks = new ArrayList<>();
        runningAtheneTasks.add(exercise1.getId());
        ReflectionTestUtils.setField(atheneService, "runningAtheneTasks", runningAtheneTasks);
        // Verify injection
        assert (atheneService.isTaskRunning(exercise1.getId()));

        // Let textSubmissionService return 10 generated submissions
        when(textSubmissionService.getTextSubmissionsByExerciseId(exercise1.getId(), true, false)).thenReturn(generateSubmissions(10));

        // generate required parameters
        List<AtheneDTO.TextBlock> blocks = generateTextBlocks(10);
        Map<Integer, TextCluster> clusters = generateClusters();

        // Catch call of atheneService to the textBlockRepository
        when(textBlockRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<TextBlock> set = invocation.getArgument(0);
            // Check for correct number of textBlocks
            if (set instanceof LinkedList) {
                assert (set.size() == 10);
            }
            else if (set instanceof ArrayList) {
                assert (set.size() == 0);
            }
            return set;
        });

        // Catch call of atheneService to the textClusterRepository
        when(textClusterRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            Collection<TextCluster> clusterCollection = invocation.getArgument(0);
            // Check for correct number of clusters
            assert (clusterCollection.size() == clusters.size());
            return new ArrayList<>(clusterCollection);
        });

        // Call test method
        atheneService.processResult(clusters, blocks, exercise1.getId());
        assert (!atheneService.isTaskRunning(exercise1.getId()));
    }

    private List<AtheneDTO.TextBlock> generateTextBlocks(int count) {
        List<AtheneDTO.TextBlock> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AtheneDTO.TextBlock newBlock = new AtheneDTO.TextBlock();
            newBlock.submissionId = i;
            newBlock.startIndex = 0;
            newBlock.endIndex = 30;
            newBlock.text = "This is an example text";
            // Calculate realistic hash (also see TextBlock.computeId())
            final String idString = newBlock.submissionId + ";" + newBlock.startIndex + "-" + newBlock.endIndex + ";" + newBlock.text;
            newBlock.id = sha1Hex(idString);
            blocks.add(newBlock);
        }

        return blocks;
    }

    private Map<Integer, TextCluster> generateClusters() {
        Map<Integer, TextCluster> clusters = new HashMap<>();
        TextCluster c1 = new TextCluster();
        clusters.put(0, c1);
        return clusters;
    }

    private List<TextSubmission> generateSubmissions(int count) {
        // Example texts for submissions
        String[] submissionTexts = new String[] {
                "Differences: \nAntipatterns: \n-Have one problem and two solutions(one problematic and one refactored)\n-Antipatterns are a sign of bad architecture and bad coding \nPattern:\n-Have one problem and one solution\n-Patterns are a sign of elaborated architecutre and coding",
                "The main difference between patterns and antipatterns is, that patterns show you a good way to do something and antipatterns show a bad way to do something. Nevertheless patterns may become antipatterns in the course of changing understanding of how good software engineering looks like. One example for that is functional decomposition, which used to be a pattern and \"good practice\". Over the time it turned out that it is not a goog way to solve problems, so it became a antipattern.\n\nA pattern itsself is a proposed solution to a problem that occurs often and in different situations.\nIn contrast to that a antipattern shows commonly made mistakes when dealing with a certain problem. Nevertheless a refactored solution is aswell proposed.",
                "1.Patterns can evolve into Antipatterns when change occurs\\n2. Pattern has one solution, whereas anti pattern can have subtypes of solution\\n3. Antipattern has negative consequences and symptom, where as patterns looks only into benefits and consequences",
                "Patterns: A way to Model code in differents ways \nAntipattern: A way of how Not to Model code",
                "Antipatterns are used when there are common mistakes in software management and development to find these, while patterns by themselves are used to build software systems in the context of frequent change by reducing complexity and isolating the change.\nAnother difference is that the antipatterns have problematic solution and then refactored solution, while patterns only have a solution.",
                "- In patterns we have a problem and a solution, in antipatterns we have a problematic solution and a refactored solution instead\n- patterns represent best practices from the industry etc. so proven concepts, whereas antipatterns shed a light on common mistakes during software development etc.",
                "1) Patterns have one solution, antipatterns have to solutions (one problematic and one refactored).\n2) for the coice of patterns code has to be written; for antipatterns, the bad smell code already exists",
                "Design Patterns:\n\nSolutions which are productive and efficient and are developed by Software Engineers over the years of practice and solving problems.\n\nAnti Patterns:\n\nKnown solutions which are actually bad or defective to certain kind of problems.",
                "Patterns has one problem and one solution.\nAntipatterns have one problematic solution and a solution for that. The antipattern happens when  a solution that is been used for a long time can not apply anymore. ",
                "Patterns identify problems and present solutions.\nAntipatterns identify problems but two kinds of solutions. One problematic solution and a better \"refactored\" version of the solution. Problematic solutions are suggested not to be used because they results in smells or hinder future work." };

        // Error handling
        if (count >= submissionTexts.length) {
            count = submissionTexts.length;
        }

        // Create Submissions with id's 0 - count
        List<TextSubmission> textSubmissions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextSubmission s = new TextSubmission((long) i).text(submissionTexts[i]);
            s.setLanguage(Language.ENGLISH);
            textSubmissions.add(s);
        }

        return textSubmissions;
    }

}
