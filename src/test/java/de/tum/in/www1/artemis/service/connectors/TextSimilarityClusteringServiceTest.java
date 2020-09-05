package de.tum.in.www1.artemis.service.connectors;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.text.*;
import de.tum.in.www1.artemis.exception.NetworkingError;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TextSimilarityClusteringServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String CLUSTERING_ENDPOINT = "http://localhost:8002/cluster";

    private final TextSimilarityClusteringService textSimilarityClusteringService = new TextSimilarityClusteringService();

    private final TextEmbeddingService textEmbeddingService = new TextEmbeddingService();

    // Sentences taken from the book Object-Oriented Software Engineering by B. Bruegge and A. Dutoit
    private final String[] sentences = {
            "The purpose of science is to describe and understand complex systems, such as a system of atoms, a society of human beings, or a solar system.",
            "Traditionally, a distinction is made between natural sciences and social sciences to distinguish between two major types of systems.",
            "The purpose of natural sciences is to understand nature and its subsystems.", "Natural sciences include, for example, biology, chemistry, physics, and paleontology.",
            "The purpose of the social sciences is to understand human beings.", "Social sciences include psychology and sociology.",
            "There is another type of system that we call an artificial system.",
            "Examples of artificial systems include the space shuttle, airline reservation systems, and stock trading systems.",
            "Herbert Simon coined the term sciences of the artificial to describe the sciences that deal with artificial systems [Simon, 1970].",
            "Whereas natural and social sciences have been around for centuries, the sciences of the artificial are recent.",
            "Computer science, for example, the science of understanding computer systems, is a child of the twentieth century.",
            "Many methods that have been successfully applied in the natural sciences and humanities can be applied to the sciences of the artificial as well.",
            "By looking at the other sciences, we can learn quite a bit.", "One of the basic methods of science is modeling.",
            "A model is an abstract representation of a system that enables us to answer questions about the system.",
            "Models are useful when dealing with systems that are too large, too small, too complicated, or too expensive to experience firsthand.",
            "Models also allow us to visualize and understand systems that either no longer exist or that are only claimed to exist." };

    private final List<TextBlock> blocks = Stream.of(sentences).map(text -> new TextBlock().text(text).startIndex(0).endIndex(text.length())).peek(TextBlock::computeId)
            .collect(toList());

    private Course course = new Course();

    private TextExercise exercise = (TextExercise) new TextExercise().course(course);

    private TextSimilarityClusteringService.Response response;

    @Test
    public void clusterTextBlocks() {
        // TODO: Split tests of embedding and clustering
        final Map<Integer, TextCluster> clusterDictionary = response.clusters;
        assertThat(clusterDictionary.keySet(), hasSize(5));
        assertThat(clusterDictionary.keySet(), hasItem(-1));

        List<List<Double>> matrix = response.distanceMatrix;
        assertThat(matrix, hasSize(blocks.size()));

        List<TextTreeNode> clusterTree = response.clusterTree;
        List<TextTreeNode> blocksInTree = clusterTree.stream().filter(TextTreeNode::isBlockNode).collect(toList());
        // Assert that number of blockNodes in the tree equals number of blocks
        assertThat(blocksInTree, hasSize(blocks.size()));
        List<Long> groupByChild = clusterTree.stream().map(TextTreeNode::getChild).distinct().collect(toList());
        // Assert that child is a unique property of a TextTreeNode
        assertThat(groupByChild, hasSize(clusterTree.size()));
        List<Long> groupByParent = clusterTree.stream().map(TextTreeNode::getParent).distinct().collect(toList());
        // Assert that parent is not a unique property of a TextTreeNode
        assertThat(groupByParent.size(), lessThan(clusterTree.size()));
    }

    @BeforeAll
    public void init() throws NetworkingError {
        assumeTrue(isTextAssessmentClusteringAvailable());
        ReflectionTestUtils.setField(textSimilarityClusteringService, "API_ENDPOINT", CLUSTERING_ENDPOINT);
        ReflectionTestUtils.setField(textEmbeddingService, "API_ENDPOINT", "http://localhost:8001/embed");
        course.setId(1L);
        exercise.setId(2L);
        List<TextEmbedding> embeddings = textEmbeddingService.embedTextBlocks(blocks, exercise);
        response = textSimilarityClusteringService.clusterTextBlocks(embeddings, 3);
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
