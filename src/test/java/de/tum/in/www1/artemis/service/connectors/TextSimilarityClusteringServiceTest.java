package de.tum.in.www1.artemis.service.connectors;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.text.*;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TextSimilarityClusteringServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String CLUSTERING_ENDPOINT = "http://localhost:8002/cluster";

    @Mock
    private RemoteArtemisServiceConnector<TextSimilarityClusteringService.Request, TextSimilarityClusteringService.Response> connector = mock(RemoteArtemisServiceConnector.class);

    private final TextSimilarityClusteringService textSimilarityClusteringService = new TextSimilarityClusteringService();

    private TextExerciseUtilService textExerciseUtilService = new TextExerciseUtilService();

    // Sentences taken from the book Object-Oriented Software Engineering by B. Bruegge and A. Dutoit
    private final String[] sentences = { "The purpose of science is to describe and understand complex systems,",
            "such as a system of atoms, a society of human beings, or a solar system.",
            "Traditionally, a distinction is made between natural sciences and social sciences to distinguish between two major types of systems.",
            "The purpose of natural sciences is to understand nature and its subsystems.", "Natural sciences include, for example, biology, chemistry, physics, and paleontology.",
            "The purpose of the social sciences is to understand human beings.", "Social sciences include psychology and sociology.",
            "There is another type of system that we call an artificial system.",
            "Examples of artificial systems include the space shuttle, airline reservation systems, and stock trading systems.",
            "Herbert Simon coined the term sciences of the artificial to describe the sciences that deal with artificial systems [Simon, 1970].",
            "Whereas natural and social sciences have been around for centuries, the sciences of the artificial are recent." };

    private final List<TextBlock> blocks = Stream.of(sentences).map(text -> new TextBlock().text(text).startIndex(0).endIndex(text.length())).peek(TextBlock::computeId)
            .collect(toList());

    private Course course = new Course();

    private TextExercise exercise = (TextExercise) new TextExercise().course(course);

    @Test
    public void clusterTextBlocks() {
        try {
            TextSimilarityClusteringService.Response response = textSimilarityClusteringService.clusterTextBlocks(new ArrayList());
            assertThat(response.clusters.keySet(), hasSize(5));
            assertThat(response.clusters.keySet(), hasItem(-1));

            assertThat(response.distanceMatrix, hasSize(blocks.size()));

            // Assert that number of blockNodes in the tree equals number of blocks
            List<TextTreeNode> blocksInTree = response.clusterTree.stream().filter(TextTreeNode::isBlockNode).collect(toList());
            assertThat(blocksInTree, hasSize(blocks.size()));
        }
        catch (NetworkingError error) {
            fail("Invoke failed with Networking Error.");
        }
    }

    @BeforeAll
    public void init() {
        ReflectionTestUtils.setField(textSimilarityClusteringService, "API_ENDPOINT", CLUSTERING_ENDPOINT);
        ReflectionTestUtils.setField(textSimilarityClusteringService, "connector", connector);
        course.setId(1L);
        exercise.setId(2L);

        // Create mock response
        TextSimilarityClusteringService.Response invokeResponse = new TextSimilarityClusteringService.Response();
        int[] clusterSizes = new int[] { 2, 3, 2, 2, 2 };
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(blocks, clusterSizes, exercise);
        invokeResponse.clusters = new LinkedHashMap<>();
        for (int i = 0; i <= 4; i++) {
            invokeResponse.clusters.put(i - 1, clusters.get(i));
        }
        try {
            invokeResponse.clusterTree = parseClusterTree(exercise);
            List<TextPairwiseDistance> pairwiseDistances = parsePairwiseDistances(exercise);
            double[][] matrix = new double[blocks.size()][blocks.size()];
            pairwiseDistances.forEach(dist -> matrix[(int) dist.getBlockI()][(int) dist.getBlockJ()] = dist.getDistance());
            invokeResponse.distanceMatrix = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                List<Double> row = new ArrayList<>();
                for (int j = 0; j < blocks.size(); j++) {
                    row.add(matrix[i][j]);
                }
                invokeResponse.distanceMatrix.add(row);
            }
        }
        catch (ParseException | IOException e) {
            fail("JSON files for clusterTree or pairwiseDistances not successfully read/parsed.");
        }

        try {
            when(connector.invokeWithRetry(eq(CLUSTERING_ENDPOINT), any(), any(), eq(1))).thenReturn(invokeResponse);
        }
        catch (NetworkingError error) {
            fail("Mocks could not be initialized.");
            return;
        }
    }

    /**
     * Reads and parses the cluster tree from json file for given exercise
     * @param exercise
     * @return list of tree nodes
     * @throws IOException
     * @throws ParseException
     */
    private static List<TextTreeNode> parseClusterTree(TextExercise exercise) throws IOException, ParseException {
        List<TextTreeNode> result = new ArrayList<>();
        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("src/test/resources/test-data/clustering/clusterTree.json");
        JSONArray treeList = (JSONArray) jsonParser.parse(reader);
        for (int i = 0; i < treeList.size(); i++) {
            JSONObject n = (JSONObject) treeList.get(i);
            TextTreeNode node = new TextTreeNode();
            node.setExercise(exercise);
            node.setParent((long) n.get("parent"));
            node.setLambdaVal((double) n.get("lambdaVal"));
            node.setChildSize((long) n.get("childSize"));
            node.setChild((long) n.get("child"));
            result.add(node);
        }
        return result;
    }

    /**
     * Reads and parses the pairwise distances from json file for given exercise
     * @param exercise
     * @return list of pairwise distances
     * @throws IOException
     * @throws ParseException
     */
    private static List<TextPairwiseDistance> parsePairwiseDistances(TextExercise exercise) throws IOException, ParseException {
        List<TextPairwiseDistance> result = new ArrayList<>();
        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("src/test/resources/test-data/clustering/pairwiseDistances.json");
        JSONArray distList = (JSONArray) jsonParser.parse(reader);
        for (int i = 0; i < distList.size(); i++) {
            JSONObject d = (JSONObject) distList.get(i);
            TextPairwiseDistance dist = new TextPairwiseDistance();
            dist.setExercise(exercise);
            dist.setDistance((double) d.get("distance"));
            dist.setBlockI((long) d.get("blockI"));
            dist.setBlockJ((long) d.get("blockJ"));
            result.add(dist);
        }
        return result;
    }
}
