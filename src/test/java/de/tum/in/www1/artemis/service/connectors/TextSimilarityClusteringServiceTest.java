package de.tum.in.www1.artemis.service.connectors;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;

public class TextSimilarityClusteringServiceTest {

    private static String API_ENDPOINT = "http://localhost:8000/cluster";

    @Test
    public void clusterTextBlocks() throws TextSimilarityClusteringService.NetworkingError {
        final TextSimilarityClusteringService service = new TextSimilarityClusteringService();
        ReflectionTestUtils.setField(service, "API_ENDPOINT", API_ENDPOINT);

        final List<TextBlock> blocks = Stream.of("foo", "bar").map(text -> new TextBlock().text(text).startIndex(0).endIndex(3)).peek(TextBlock::computeId).collect(toList());

        final Map<Integer, TextCluster> clusterDictionary = service.clusterTextBlocks(blocks);

        assertThat(clusterDictionary.keySet(), hasSize(1));
        assertThat(clusterDictionary.keySet(), hasItem(-1));
        final TextCluster cluster = clusterDictionary.get(-1);
        final List<TextBlock> blocks1 = cluster.getBlocks();

        assertThat(blocks1.toArray(), is(equalTo(blocks.toArray())));

        final double[][] distanceMatrix = cluster.getDistanceMatrix();
        assertThat(distanceMatrix[0][1], is(equalTo(distanceMatrix[1][0])));
        assertThat(distanceMatrix[0][1], is(both(greaterThan(0.6)).and(lessThan(0.8))));
    }

    @BeforeClass
    public static void runClassOnlyIfTextAssessmentClusteringIsAvailable() {
        assumeTrue(isTextAssessmentClusteringAvailable());
    }

    private static boolean isTextAssessmentClusteringAvailable() {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(API_ENDPOINT).openConnection();
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
