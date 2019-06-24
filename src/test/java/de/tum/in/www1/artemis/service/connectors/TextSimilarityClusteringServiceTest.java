package de.tum.in.www1.artemis.service.connectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class TextSimilarityClusteringServiceTest {

    @Ignore
    @Test
    public void clusterTextBlocks() throws TextSimilarityClusteringService.NetworkingError, IOException {
        final TextSimilarityClusteringService service = new TextSimilarityClusteringService();
        ReflectionTestUtils.setField(service, "API_ENDPOINT", "http://127.0.0.1:8000/cluster");

        final List<TextSimilarityClusteringService.TextBlock> blocks = Stream.of("foo", "bar").map(text -> {
            final TextSimilarityClusteringService.TextBlock textBlock = new TextSimilarityClusteringService.TextBlock();
            textBlock.text = text;
            return textBlock;
        }).collect(Collectors.toList());

        final Map<Integer, TextSimilarityClusteringService.Cluster> clusterDictionary = service.clusterTextBlocks(blocks);

        assertThat(clusterDictionary.keySet(), hasSize(1));
        assertThat(clusterDictionary.keySet(), hasItem(-1));
        final TextSimilarityClusteringService.TextBlock[] blocks1 = clusterDictionary.get(-1).blocks;

        assertThat(blocks1, is(equalTo(blocks.toArray())));
    }
}
