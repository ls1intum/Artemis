package de.tum.in.www1.artemis.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.ase.athene.protobuf.AtheneResponse;
import de.tum.in.ase.athene.protobuf.Segment;
import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.TextBlockService;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import de.tum.in.www1.artemis.user.UserUtilService;

class AtheneIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "atheneintegration";

    @Value("${artemis.athene.base64-secret}")
    private String atheneApiSecret;

    @Autowired
    private TextBlockService textBlockService;

    @Autowired
    private AtheneService atheneService;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    /**
     * Test that Added Distances are calculated and positionInCluster is stored when receiving
     * response from Athene.
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    void testProcessingClusterAddedDistances() throws Exception {
        SecurityUtils.setAuthorizationObject();
        int numberOfStudents = 5;

        userUtilService.addUsers(TEST_PREFIX, numberOfStudents, 0, 0, 0);
        final var course = textExerciseUtilService.addCourseWithOneFinishedTextExercise();
        final var exercise = (TextExercise) course.getExercises().iterator().next();
        final var textSubmissions = ParticipationFactory.generateTextSubmissions(numberOfStudents);
        for (int i = 0; i < textSubmissions.size(); i++) {
            final var submission = textSubmissions.get(i);
            submission.setId(null);
            submission.submitted(true);
            participationUtilService.addSubmission(exercise, submission, String.format("%sstudent%d", TEST_PREFIX, i + 1));
        }

        final var atheneResultBuilder = AtheneResponse.newBuilder();

        final var segments = textSubmissions.stream().map(textBlockService::splitSubmissionIntoBlocks).flatMap(Collection::stream)
                .map(block -> Segment.newBuilder().setId(block.getId()).setSubmissionId(block.getSubmission().getId().intValue()).setText(block.getText())
                        .setStartIndex(block.getStartIndex()).setEndIndex(block.getEndIndex()).build())
                .collect(Collectors.toCollection(ArrayList::new));
        atheneResultBuilder.addAllSegments(segments);

        List.of(0, 1, 2).forEach(cid -> {
            var clusterBuilder = atheneResultBuilder.addClustersBuilder();
            segments.subList(cid * 3, (cid + 1) * 3).forEach(segment -> clusterBuilder.addSegmentsBuilder().setId(segment.getId()).build());

            final float[][] matrix = { { 0.0f, 0.1f, 0.2f }, { 0.1f, 0.0f, 0.2f }, { 0.2f, 0.1f, 0.0f } };
            for (int x = 0; x < matrix.length; x++) {
                for (int y = 0; y < matrix[x].length; y++) {
                    clusterBuilder.addDistanceMatrixBuilder().setX(x).setY(y).setValue(matrix[x][y]).build();
                }
            }

            clusterBuilder.build();
        });

        List<Long> runningAtheneTasks = new ArrayList<>();
        runningAtheneTasks.add(exercise.getId());
        ReflectionTestUtils.setField(atheneService, "runningAtheneTasks", runningAtheneTasks);

        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", atheneApiSecret);
        AtheneResponse atheneResponse = atheneResultBuilder.build();
        request.postWithoutLocation("/api/athene-result/" + exercise.getId(), atheneResponse.toByteArray(), HttpStatus.OK, httpHeaders, "application/x-protobuf");

        final List<TextCluster> clusters = textClusterRepository.findAllByExercise(exercise);

        for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {
            TextCluster cluster = clusters.get(clusterIndex);
            assertThat(cluster.size()).isEqualTo(3);
            assertThat(cluster.getBlocks()).hasSize(3);

            List<TextBlock> blocks = cluster.getBlocks();
            for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                TextBlock block = blocks.get(blockIndex);
                assertThat(block.getAddedDistance()).isGreaterThan(1.65);
                Segment segment = atheneResponse.getClusters(clusterIndex).getSegmentsList().get(blockIndex);
                assertThat(block.getId()).isEqualTo(segment.getId());
                assertThat(block).hasFieldOrPropertyWithValue("positionInCluster", blockIndex);
            }
        }

    }
}
