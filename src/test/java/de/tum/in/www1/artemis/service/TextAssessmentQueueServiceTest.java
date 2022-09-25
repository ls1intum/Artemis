package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;

class TextAssessmentQueueServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextAssessmentQueueService textAssessmentQueueService;

    @Autowired
    private TextSubmissionService textSubmissionService;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Random random;

    private Percentage errorRate;

    private Course course;

    @BeforeEach
    void init() {
        database.addUsers(1, 1, 1, 1);
        course = database.addCourseWithOneReleasedTextExercise();
        random = new Random();
        errorRate = Percentage.withPercentage(0.0001);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    void calculateAddedDistancesTest() {
        var textBlocks = new ArrayList<>(textExerciseUtilService.generateTextBlocks(4));
        TextCluster textCluster = addTextBlocksToRandomCluster(textBlocks, 1).get(0);
        double[][] distanceMatrix = new double[][] { { 0, 0.1, 0.2, 0.3 }, { 0.1, 0, 0.4, 0.5 }, { 0.2, 0.4, 0, 0.6 }, { 0.3, 0.5, 0.6, 0 } };
        textCluster.setDistanceMatrix(distanceMatrix);
        textAssessmentQueueService.setAddedDistances(textBlocks, textCluster);
        assertThat(textBlocks.get(0).getAddedDistance()).isCloseTo(1 - 0.1 + 1 - 0.2 + 1 - 0.3, errorRate);
        assertThat(textBlocks.get(1).getAddedDistance()).isCloseTo(1 - 0.1 + 1 - 0.4 + 1 - 0.5, errorRate);
        assertThat(textBlocks.get(2).getAddedDistance()).isCloseTo(1 - 0.2 + 1 - 0.4 + 1 - 0.6, errorRate);
        assertThat(textBlocks.get(3).getAddedDistance()).isCloseTo(1 - 0.3 + 1 - 0.5 + 1 - 0.6, errorRate);
    }

    @Test
    void testTextBlockProbabilities() {
        var textBlocks = new ArrayList<>(textExerciseUtilService.generateTextBlocks(4));
        TextCluster textCluster = addTextBlocksToRandomCluster(textBlocks, 1).get(0);
        var probabilities = new double[] { 1.0d, 2.0d };
        textCluster.setProbabilities(probabilities);
        assertThat(textCluster.getProbabilities()).isEqualTo(probabilities);
    }

    @Test
    // Note: this transaction is necessary, because the method call textSubmissionService.getTextSubmissionsByExerciseId does not eagerly load the text blocks that are
    // evaluated in the call textAssessmentQueueService.calculateSmallerClusterPercentageBatch
    // TODO: we should remove transactions in the corresponding production code and make sure to eagerly load text blocks with the submission in such a case
    @Transactional(readOnly = true)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void calculateSmallerClusterPercentageTest() {
        int submissionCount = 5;
        int submissionSize = 4;
        int[] clusterSizes = new int[] { 4, 5, 10, 1 };
        var textBlocks = new ArrayList<>(textExerciseUtilService.generateTextBlocks(submissionCount * submissionSize));
        TextExercise textExercise = textExerciseUtilService.createSampleTextExerciseWithSubmissions(course, textBlocks, submissionCount, submissionSize);
        textBlocks.forEach(TextBlock::computeId);
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(new HashSet<>(textBlocks), clusterSizes, textExercise);
        textClusterRepository.saveAll(clusters);
        textBlockRepository.saveAll(textBlocks);
        List<TextSubmission> textSubmissions = textSubmissionService.getAllSubmissionsForExercise(textExercise.getId(), true, false);
        Map<TextBlock, Double> smallerClusterPercentages = textAssessmentQueueService.calculateSmallerClusterPercentageBatch(textSubmissions);
        textBlocks.forEach(textBlock -> {
            if (textBlock.getCluster() == clusters.get(0)) {
                // cluster has size 4 Therefore 25% are smaller
                assertThat(smallerClusterPercentages.get(textBlock)).isCloseTo(0.25, errorRate);
            }
            else if (textBlock.getCluster() == clusters.get(1)) {
                // cluster has size 5 Therefore 50% are smaller
                assertThat(smallerClusterPercentages.get(textBlock)).isCloseTo(0.5, errorRate);
            }
            else if (textBlock.getCluster() == clusters.get(2)) {
                // cluster has size 10 Therefore 100% are smaller
                assertThat(smallerClusterPercentages.get(textBlock)).isCloseTo(1.0, errorRate);
            }
            else if (textBlock.getCluster() == clusters.get(3)) {
                // cluster has size 1 Therefore 0% are smaller
                assertThat(smallerClusterPercentages.get(textBlock)).isCloseTo(0.0, errorRate);
            }
            else {
                assert false;
            }
        });
    }

    private List<TextCluster> addTextBlocksToRandomCluster(List<TextBlock> textBlocks, int clusterCount) {
        ArrayList<TextCluster> clusters = new ArrayList<>();
        for (int i = 0; i < clusterCount; i++) {
            clusters.add(new TextCluster());
        }
        // Add all text blocks to a random cluster
        textBlocks.forEach(textBlock -> clusters.get(random.nextInt(clusterCount)).addBlocks(textBlock));
        return clusters;
    }

}
