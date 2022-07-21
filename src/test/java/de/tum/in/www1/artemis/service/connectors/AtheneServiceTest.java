package de.tum.in.www1.artemis.service.connectors;

import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.ase.athene.protobuf.Cluster;
import de.tum.in.ase.athene.protobuf.DistanceMatrixEntry;
import de.tum.in.ase.athene.protobuf.Segment;
import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AtheneRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import de.tum.in.www1.artemis.util.ModelFactory;

class AtheneServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AtheneRequestMockProvider atheneRequestMockProvider;

    @Autowired
    private StudentParticipationRepository participationRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private AtheneService atheneService;

    private TextExercise exercise1;

    /**
     * Initializes atheneService and example exercise
     */
    @BeforeEach
    void init() {
        // Create example exercise
        database.addUsers(10, 1, 0, 1);
        var course = database.addCourseWithOneReleasedTextExercise();
        exercise1 = (TextExercise) course.getExercises().iterator().next();
        atheneRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() {
        atheneRequestMockProvider.reset();
        atheneService.finishTask(exercise1.getId());
        database.resetDatabase();
    }

    /**
     * Submits a job to atheneService without any submissions
     */
    @Test
    void submitJobWithoutSubmissions() {
        atheneService.submitJob(exercise1);
        assertThat(!atheneService.isTaskRunning(exercise1.getId())).isTrue();
    }

    private List<TextSubmission> generateTextSubmissions(int size) {
        var textSubmissions = ModelFactory.generateTextSubmissions(size);
        for (var i = 0; i < size; i++) {
            var textSubmission = textSubmissions.get(i);
            textSubmission.setId((long) (i + 1));
            var student = database.getUserByLogin("student" + (i + 1));
            var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise1, student);
            participation = participationRepository.save(participation);
            textSubmission.setParticipation(participation);
            textSubmission.setSubmitted(true);
        }

        return textSubmissionRepository.saveAll(textSubmissions);
    }

    /**
     * Submits a job to atheneService with less than 10 submissions (will use fallback segmentation without athene)
     */
    @Test
    void submitJobWithLessThan10Submissions() {
        generateTextSubmissions(9);
        atheneService.submitJob(exercise1);
        assertThat(atheneService.isTaskRunning(exercise1.getId())).isFalse();
    }

    /**
     * Submits a job to atheneService with 10 submissions (will trigger athene)
     */
    @Test
    void submitJobWith10Submissions() {
        generateTextSubmissions(10);

        // Create mock server
        atheneRequestMockProvider.mockSubmitSubmissions();

        atheneService.submitJob(exercise1);
        assertThat(atheneService.isTaskRunning(exercise1.getId())).isTrue();
    }

    /**
     * Tests parseTextBlocks of atheneService
     */
    @Test
    void parseTextBlocks() {
        int size = 10;
        var textSubmissions = generateTextSubmissions(size);

        List<Segment> segments = generateSegments(textSubmissions);
        List<TextBlock> textBlocks = atheneService.parseTextBlocks(segments, exercise1.getId());
        for (TextBlock textBlock : textBlocks) {
            assertThat(textBlock.getId()).isNotNull();
            assertThat(textBlock.getText()).isNotNull();
            assertThat(textBlock.getType()).isEqualTo(TextBlockType.AUTOMATIC);
            assertThat(textBlock.getSubmission()).isNotNull();
        }
        assertThat(textBlocks).hasSize(size);
    }

    /**
     * Tests parseTextClusters of atheneService
     */

    @Test
    void parseTextClusters() {
        List<Cluster> clusters = generateClusters();
        List<TextCluster> textClusters = atheneService.parseTextClusters(clusters);
        for (TextCluster textCluster : textClusters) {
            assertThat(textCluster.getBlocks()).isNotNull();
            assertThat(textCluster.getBlocks()).isNotEmpty();
            assertThat(textCluster.getDistanceMatrix()).isNotNull();
        }
        assertThat(textClusters).hasSameSizeAs(clusters);
    }

    /**
     * Generates example AtheneDTO TextBlocks
     *
     * @param textSubmissions How many blocks should be generated
     * @return A list containing the generated TextBlocks
     */
    private List<Segment> generateSegments(List<TextSubmission> textSubmissions) {
        return textSubmissions.stream().map(textSubmission -> {
            final String idString = textSubmission.getId() + ";0-30;" + textSubmission.getText().substring(0, 30);
            return Segment.newBuilder().setId(sha1Hex(idString)).setSubmissionId(textSubmission.getId().intValue()).setStartIndex(0).setEndIndex(30)
                    .setText(textSubmission.getText().substring(0, 30)).build();
        }).toList();
    }

    /**
     * Generates example protobuf Clusters
     *
     * @return A List with the generated Clusters
     */
    private List<Cluster> generateClusters() {
        int size = 10;
        List<Cluster> clusters = new ArrayList<>();
        // Generate clusters
        for (int i = 0; i < size; i++) {
            List<Segment> segments = new ArrayList<>();
            // First generate 5 segments for each cluster
            for (int j = 0; j < 5; j++) {
                Segment segment = Segment.newBuilder().setId(String.valueOf(j + 1)).build();
                segments.add(segment);
            }
            // Create a cluster
            Cluster cluster = Cluster.newBuilder().addAllSegments(segments).addAllDistanceMatrix(generateDistanceMatrix(5)).build();
            clusters.add(cluster);
        }
        return clusters;
    }

    /**
     * Generates example protobuf DistanceMatrix with DistanceMatrixEntries
     *
     * @param size The size of the matrix (size x size)
     * @return generated DistanceMatrix (List of DistanceMatrixEntries)
     */
    private List<DistanceMatrixEntry> generateDistanceMatrix(int size) {
        List<DistanceMatrixEntry> distanceMatrix = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j <= i; j++) {
                if (i == j) {
                    DistanceMatrixEntry diagonalEntry = DistanceMatrixEntry.newBuilder().setX(i).setY(j).setValue(0).build();
                    distanceMatrix.add(diagonalEntry);
                }
                else {
                    DistanceMatrixEntry entry = DistanceMatrixEntry.newBuilder().setX(i).setY(j).setValue(i + 5).build();
                    DistanceMatrixEntry symmetricalEntry = DistanceMatrixEntry.newBuilder().setX(j).setY(i).setValue(i + 5).build();
                    distanceMatrix.add(entry);
                    distanceMatrix.add(symmetricalEntry);
                }
            }
        }
        return distanceMatrix;
    }

}
