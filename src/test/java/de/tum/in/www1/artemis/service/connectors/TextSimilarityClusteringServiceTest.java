package de.tum.in.www1.artemis.service.connectors;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;

public class TextSimilarityClusteringServiceTest {

    @Ignore
    @Test
    public void clusterTextBlocks() throws TextSimilarityClusteringService.NetworkingError, IOException {
        final TextSimilarityClusteringService service = new TextSimilarityClusteringService();
        ReflectionTestUtils.setField(service, "API_ENDPOINT", "https://tac.ase.in.tum.de/cluster");

        final List<TextBlock> blocks = Stream.of("foo", "bar").map(text -> new TextBlock().text(text).startIndex(0).endIndex(3)).peek(TextBlock::computeId).collect(toList());

        final Map<Integer, TextCluster> clusterDictionary = service.clusterTextBlocks(blocks);

        assertThat(clusterDictionary.keySet(), hasSize(1));
        assertThat(clusterDictionary.keySet(), hasItem(-1));
        final TextCluster cluster = clusterDictionary.get(-1);
        final List<TextBlock> blocks1 = cluster.getBlocks();

        assertThat(blocks1.toArray(), is(equalTo(blocks.toArray())));

        final double[][] distanceMatrix = cluster.getDistanceMatrix();
        assertThat(distanceMatrix[0][1], is(equalTo(distanceMatrix[1][0])));
        assertThat((int) (distanceMatrix[0][1] * 10), is(equalTo(6)));
    }
}
