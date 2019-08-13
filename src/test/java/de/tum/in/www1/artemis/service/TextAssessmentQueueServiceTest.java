package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.repository.TextClusterRepository;

public class TextAssessmentQueueServiceTest {

    private TextAssessmentQueueService textAssessmentQueueService;

    @MockBean
    private ParticipationService participationServiceMock;

    @MockBean
    private TextClusterRepository textClusterRepositoryMock;

    @BeforeEach
    public void init() {
        textAssessmentQueueService = new TextAssessmentQueueService(participationServiceMock, textClusterRepositoryMock);
    }

    @Test
    public void calculateAddedDistancesTest() {
        ArrayList<TextBlock> textBlocks = generateTextBlocks(4);
        TextCluster textCluster = addTextBlocksToTextCluster(textBlocks);
        double[][] distanceMatrix = new double[][] { { 0, 0.1, 0.2, 0.3 }, { 0.1, 0, 0.4, 0.5 }, { 0.2, 0.4, 0, 0.6 }, { 0.3, 0.5, 0.6, 0 } };
        textCluster.setDistanceMatrix(distanceMatrix);
        textAssessmentQueueService.setAddedDistances(textBlocks, textCluster);
        Percentage errorRate = Percentage.withPercentage(0.0001);
        assertThat(textBlocks.get(0).getAddedDistance()).isCloseTo(1 - 0.1 + 1 - 0.2 + 1 - 0.3, errorRate);
        assertThat(textBlocks.get(1).getAddedDistance()).isCloseTo(1 - 0.1 + 1 - 0.4 + 1 - 0.5, errorRate);
        assertThat(textBlocks.get(2).getAddedDistance()).isCloseTo(1 - 0.2 + 1 - 0.4 + 1 - 0.6, errorRate);
        assertThat(textBlocks.get(3).getAddedDistance()).isCloseTo(1 - 0.3 + 1 - 0.5 + 1 - 0.6, errorRate);

    }

    private TextCluster addTextBlocksToTextCluster(ArrayList<TextBlock> textBlocks) {
        TextCluster cluster = new TextCluster();
        textBlocks.forEach(textBlock -> {
            cluster.addBlocks(textBlock);
            textBlock.setCluster(cluster);
        });
        return cluster;
    }

    private ArrayList<TextBlock> generateTextBlocks(int count) {
        ArrayList<TextBlock> textBlocks = new ArrayList<>();
        TextBlock textBlock;
        for (int i = 0; i < count; i++) {
            textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

}
