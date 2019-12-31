package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.util.*;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.TextClusterRepository;

public class TextAssessmentQueueServiceTest extends AbstractSpringIntegrationTest {

    @Autowired
    private TextAssessmentQueueService textAssessmentQueueService;

    @SpyBean
    private ParticipationService participationService;

    @SpyBean
    private TextSubmissionService textSubmissionService;

    // TODO: this mock and the spies above increase the test execution time by ~30s, because the whole application needs to restart twice.
    @MockBean
    private TextClusterRepository textClusterRepository;

    private Random random;

    private Percentage errorRate;

    @BeforeEach
    public void init() {
        random = new Random();
        errorRate = Percentage.withPercentage(0.0001);
    }

    @Test
    public void calculateAddedDistancesTest() {
        ArrayList<TextBlock> textBlocks = generateTextBlocks(4);
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
    public void calculateSmallerClusterPercentageTest() {
        int submissionCount = 5;
        int submissionSize = 4;
        int[] clusterSizes = new int[] { 4, 5, 10, 1 };
        ArrayList<TextBlock> textBlocks = generateTextBlocks(submissionCount * submissionSize);
        TextExercise textExercise = createSampleTextExercise(textBlocks, submissionCount, submissionSize);
        List<TextSubmission> textSubmissions = textSubmissionService.getTextSubmissionsByExerciseId(textExercise.getId(), true);
        List<TextCluster> clusters = addTextBlocksToCluster(textBlocks, clusterSizes);
        // TODO: can we not just save the text clusters into the database here?
        doReturn(clusters).when(textClusterRepository).findAllByExercise(textExercise);
        HashMap<TextBlock, Double> smallerClusterPercentages = textAssessmentQueueService.calculateSmallerClusterPercentageBatch(textSubmissions);
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
        // Add all textblocks to a random cluster
        textBlocks.forEach(textBlock -> {
            clusters.get(random.nextInt(clusterCount)).addBlocks(textBlock);
        });
        return clusters;
    }

    private List<TextCluster> addTextBlocksToCluster(List<TextBlock> textBlocks, int[] clusterSizes) {

        if (Arrays.stream(clusterSizes).sum() != textBlocks.size()) {
            throw new IllegalArgumentException("The clusterSizes sum has to be equal to the number of textBlocks");
        }

        // Create clusters
        ArrayList<TextCluster> clusters = new ArrayList<>();
        for (int i = 0; i < clusterSizes.length; i++) {
            clusters.add(new TextCluster());
        }
        // Add all textblocks to a random cluster

        textBlocks.forEach(textBlock -> {
            int clusterIndex = random.nextInt(clusterSizes.length);
            // as long as cluster is full select another cluster
            while (clusterSizes[clusterIndex] == 0) {
                clusterIndex = random.nextInt(clusterSizes.length);
            }
            clusterSizes[clusterIndex]--;
            clusters.get(clusterIndex).addBlocks(textBlock);
        });
        return clusters;
    }

    private TextExercise createSampleTextExercise(ArrayList<TextBlock> textBlocks, int submissionCount, int submissionSize) {
        if (textBlocks.size() != submissionCount * submissionSize) {
            throw new IllegalArgumentException("number of textBlocks must be eqaul to submissionCount * submissionSize");
        }
        TextExercise textExercise = new TextExercise();
        textExercise.setId(random.nextLong());
        TextSubmission[] submissions = new TextSubmission[submissionCount];
        StudentParticipation[] studentParticipations = new StudentParticipation[submissionCount];

        // submissions.length must be equal to studentParticipations.length;
        for (int i = 0; i < submissions.length; i++) {
            TextSubmission submission = new TextSubmission();
            StudentParticipation studentParticipation = new StudentParticipation();

            submission.setParticipation(studentParticipation);
            submission.setBlocks(textBlocks.subList(i * submissionSize, (i + 1) * submissionSize));
            textBlocks.subList(i * submissionSize, (i + 1) * submissionSize).forEach(textBlock -> textBlock.setSubmission(submission));

            studentParticipation.addSubmissions(submission);
            studentParticipation.setExercise(textExercise);
            submissions[i] = submission;
            studentParticipations[i] = studentParticipation;

        }
        // TODO: why do we actually need this? Can we not just normally use these service methods and save the data to the database before?
        doReturn(Arrays.asList(studentParticipations)).when(participationService).findByExerciseId(textExercise.getId());
        doReturn(Arrays.asList(submissions)).when(textSubmissionService).getTextSubmissionsByExerciseId(textExercise.getId(), true);
        return textExercise;
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
