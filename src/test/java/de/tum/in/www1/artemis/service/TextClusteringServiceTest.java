package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.text.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.TextEmbeddingService;
import de.tum.in.www1.artemis.service.connectors.TextSegmentationService;
import de.tum.in.www1.artemis.service.connectors.TextSimilarityClusteringService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

import de.tum.in.www1.artemis.util.ModelFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Optional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TextClusteringServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    // Sentences taken from the book Object-Oriented Software Engineering by B. Bruegge and A. Dutoit
    private static final String[] submissionText = {
        "The purpose of science is to describe and understand complex systems, such as a system of atoms, a society of human beings, or a solar system.",
        "Traditionally, a distinction is made between natural sciences and social sciences to distinguish between two major types of systems.",
        "The purpose of natural sciences is to understand nature and its subsystems. " +
            "Natural sciences include, for example, biology, chemistry, physics, and paleontology.",
        "The purpose of the social sciences is to understand human beings. " +
            "Social sciences include psychology and sociology.",
        "There is another type of system that we call an artificial system. " +
            "Examples of artificial systems include the space shuttle, airline reservation systems, and stock trading systems.",
        "Herbert Simon coined the term sciences of the artificial to describe the sciences that deal with artificial systems [Simon, 1970]. " +
            "Whereas natural and social sciences have been around for centuries, the sciences of the artificial are recent.",
        "Computer science, for example, the science of understanding computer systems, is a child of the twentieth century. " +
            "Many methods that have been successfully applied in the natural sciences and humanities can be applied to the sciences of the artificial as well.",
        "By looking at the other sciences, we can learn quite a bit. " +
            "One of the basic methods of science is modeling.",
        "A model is an abstract representation of a system that enables us to answer questions about the system. " +
            "Models are useful when dealing with systems that are too large, too small, too complicated, or too expensive to experience firsthand.",
        "Models also allow us to visualize and understand systems that either no longer exist or that are only claimed to exist."
    };

    private static final String SEGMENTATION_ENDPOINT = "http://localhost:8000/segment";

    private static final String EMBEDDING_ENDPOINT = "http://localhost:8001/embed";

    private static final String CLUSTERING_ENDPOINT = "http://localhost:8002/cluster";

    @Autowired
    TextClusteringService textClusteringService;

    @Autowired
    TextSegmentationService textSegmentationService;

    @Autowired
    TextEmbeddingService textEmbeddingService;

    @Autowired
    TextSimilarityClusteringService textSimilarityClusteringService;

    @Autowired
    TextExerciseRepository textExerciseRepository;

    @Autowired
    TextBlockRepository textBlockRepository;

    @Autowired
    TextClusterRepository textClusterRepository;

    @Autowired
    TextTreeNodeRepository textTreeNodeRepository;

    @Autowired
    TextPairwiseDistanceRepository textPairwiseDistanceRepository;

    @Autowired
    TextSubmissionRepository textSubmissionRepository;

    @Autowired
    DatabaseUtilService database;

    private List<TextExercise> exercises;
    private List<TextBlock> blocks;
    private List<TextCluster> clusters;
    private List<TextTreeNode> treeNodes;
    private List<TextPairwiseDistance> pairwiseDistances;
    private TextSubmission submission;


    @BeforeAll
    public void init() {
        assumeTrue(isTextAssessmentClusteringAvailable());
        database.addUsers(10, 0, 0);
        database.addCourseWithOneFinishedTextExercise();
        database.addCourseWithOneFinishedTextExercise();

        ReflectionTestUtils.setField(textSegmentationService, "API_ENDPOINT", SEGMENTATION_ENDPOINT);
        ReflectionTestUtils.setField(textEmbeddingService, "API_ENDPOINT", EMBEDDING_ENDPOINT);
        ReflectionTestUtils.setField(textSimilarityClusteringService, "API_ENDPOINT", CLUSTERING_ENDPOINT);

        exercises = textExerciseRepository.findAll();

        // Initialize data for the main exercise
        TextExercise exercise = exercises.get(0);
        for(int i = 1; i <= 10; i++) {
            TextSubmission submission = ModelFactory.generateTextSubmission(submissionText[i - 1], Language.ENGLISH, true);
            database.addTextSubmission(
                exercise,
                submission,
                "student" + i
            );
        }
        textClusteringService.calculateClusters(exercise);

        // Initialize data for the second exercise
        TextExercise exercise2 = exercises.get(1);
        submission = ModelFactory.generateTextSubmission(submissionText[0], Language.ENGLISH, true);
        database.addTextSubmission(
            exercise2,
            submission,
            "student" + 1
        );
        TextBlock block = ModelFactory.generateTextBlock(0,1,"b1");
        block.setSubmission(submission);
        block.setTreeId(11);
        textBlockRepository.save(block);
        TextCluster cluster = new TextCluster().exercise(exercise2);
        textClusterRepository.save(cluster);
        TextTreeNode incorrectTreeNode = new TextTreeNode().exercise(exercise2);
        incorrectTreeNode.setId(1001L);
        incorrectTreeNode.setChild(-1);
        textTreeNodeRepository.save(incorrectTreeNode);
        TextPairwiseDistance incorrectPairwiseDistance = new TextPairwiseDistance().exercise(exercise2);
        incorrectPairwiseDistance.setId(1001L);
        textPairwiseDistanceRepository.save(incorrectPairwiseDistance);

        blocks = textBlockRepository.findAllBySubmission_Participation_Exercise_IdAndTreeIdNotNull(exercise.getId());
        clusters = textClusterRepository.findAllByExercise(exercise);
        treeNodes = textTreeNodeRepository.findAllByExercise(exercise);
        pairwiseDistances = textPairwiseDistanceRepository.findAllByExercise(exercise);
    }

    @AfterAll
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void testUniqueProperties() {
        TextExercise exercise = exercises.get(0);
        // Only half of the matrix is stored in the database, as it is symmetrical.
        int matrixSize = 0;
        for(int i = 1; i < blocks.size(); i++) {
            matrixSize += i;
        }
        assertThat(pairwiseDistances, hasSize(matrixSize));

        // Getter and setter for lambda value tested
        TextTreeNode testNode = new TextTreeNode();
        testNode.setLambda_val(Double.POSITIVE_INFINITY);
        assertThat(ReflectionTestUtils.getField(testNode, "lambda_val"), equalTo(-1.));
        assertThat(testNode.getLambda_val(), equalTo(Double.POSITIVE_INFINITY));
        // isBlockNode() tested
        testNode.setChild_size(1);
        assertThat(testNode.isBlockNode(), equalTo(true));
        testNode.setChild_size(2);
        assertThat(testNode.isBlockNode(), equalTo(false));

        // The following should hold for the root node:
        //      parent == -1
        //      child == blocks.size()
        //      lambda_val == -1 (getLambda_val should return POSITIVE_INFINITY)
        //      child_size == blocks.size()
        TextTreeNode rootNode = textTreeNodeRepository.findAllByParentAndExercise(-1L, exercise).get(0);
        assertThat((int) rootNode.getChild(), equalTo(blocks.size()));
        assertThat(rootNode.getLambda_val(), equalTo(Double.POSITIVE_INFINITY));
        assertThat(rootNode.isBlockNode(), equalTo(false));
        assertThat((int) rootNode.getChild_size(), equalTo(blocks.size()));
    }

    @Test
    public void testStateAfterClustering() {
        TextExercise exercise = exercises.get(0);
        TextExercise exercise2 = exercises.get(1);

        TextBlock block1 = ModelFactory.generateTextBlock(0,1,"b1");
        block1.setSubmission(blocks.get(0).getSubmission());
        textBlockRepository.save(block1);

        TextBlock block2 = ModelFactory.generateTextBlock(0,1,"b2");
        block2.setTreeId(10);
        textBlockRepository.save(block2);

        List<TextBlock> currentBlocks = textBlockRepository.findAllBySubmission_Participation_Exercise_IdAndTreeIdNotNull(exercise.getId());
        assertThat(currentBlocks, hasSize(blocks.size()));
        assertThat(currentBlocks.contains(block1), equalTo(false));
        assertThat(currentBlocks.contains(block2), equalTo(false));
        textBlockRepository.delete(block1);
        textBlockRepository.delete(block2);

        TextCluster cluster = new TextCluster().exercise(exercise2);
        textClusterRepository.save(cluster);
        List<TextCluster> currentClusters = textClusterRepository.findAllByExercise(exercise);
        assertThat(currentClusters, hasSize(clusters.size()));
        assertThat(currentClusters.contains(cluster), equalTo(false));
        textClusterRepository.delete(cluster);

        TextTreeNode incorrectTreeNode = new TextTreeNode().exercise(exercise2);
        incorrectTreeNode.setId(1000L);
        incorrectTreeNode.setChild(-2);
        textTreeNodeRepository.save(incorrectTreeNode);
        List<TextTreeNode> currentTreeNodes = textTreeNodeRepository.findAllByExercise(exercise);
        assertThat(currentTreeNodes, hasSize(treeNodes.size()));
        assertThat(currentTreeNodes.contains(incorrectTreeNode), equalTo(false));
        textTreeNodeRepository.delete(incorrectTreeNode);

        TextPairwiseDistance incorrectPairwiseDistance = new TextPairwiseDistance().exercise(exercise2);
        incorrectPairwiseDistance.setId(1000L);
        textPairwiseDistanceRepository.save(incorrectPairwiseDistance);
        List<TextPairwiseDistance> currentPairwiseDistances = textPairwiseDistanceRepository.findAllByExercise(exercise);
        assertThat(currentPairwiseDistances, hasSize(pairwiseDistances.size()));
        assertThat(currentPairwiseDistances.contains(incorrectPairwiseDistance), equalTo(false));
        textPairwiseDistanceRepository.delete(incorrectPairwiseDistance);
    }

    @Test
    public void testRemoval() {
        TextExercise exercise = exercises.get(1);

        // Test cascading removals
        textSubmissionRepository.delete(submission);
        assertThat(textBlockRepository.findAllBySubmission_Participation_Exercise_IdAndTreeIdNotNull(exercise.getId()), hasSize(0));
        assertThat(textSubmissionRepository.findById(submission.getId()), equalTo(Optional.empty()));

        textExerciseRepository.delete(exercise);
        assertThat(textExerciseRepository.findById(exercise.getId()).isPresent(), equalTo(false));
        assertThat(textClusterRepository.findAllByExercise(exercise), hasSize(0));
        assertThat(textTreeNodeRepository.findAllByExercise(exercise), hasSize(0));
        assertThat(textPairwiseDistanceRepository.findAllByExercise(exercise), hasSize(0));
    }

    private static boolean isTextAssessmentClusteringAvailable() {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(CLUSTERING_ENDPOINT).openConnection();
            httpURLConnection.setRequestMethod("HEAD");
            httpURLConnection.setConnectTimeout(1000);
            final int responseCode = httpURLConnection.getResponseCode();

            return (responseCode == 405);
        }
        catch (IOException e) {
            return false;
        }
    }
}
