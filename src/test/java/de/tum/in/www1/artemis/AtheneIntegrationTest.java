package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.config.Constants.ATHENE_RESULT_API_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.TextBlockService;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.AtheneDTO;

public class AtheneIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Value("${artemis.athene.base64-secret}")
    private String atheneApiSecret;

    @Autowired
    private TextBlockService textBlockService;

    @Autowired
    private AtheneService atheneService;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    /**
     * Test that Added Distances are calculated and positionInCluster is stored when receiving
     * response from Athene.
     *
     * @throws Exception might be thrown from Network Call to Artemis API
     */
    @Test
    public void testProcessingClusterAddedDistances() throws Exception {
        SecurityUtils.setAuthorizationObject();
        database.addUsers(10, 0, 0);
        final var course = database.addCourseWithOneFinishedTextExercise();
        final var exercise = (TextExercise) course.getExercises().iterator().next();
        final var textSubmissions = ModelFactory.generateTextSubmissions(10);
        for (int i = 0; i < textSubmissions.size(); i++) {
            final var submission = textSubmissions.get(i);
            submission.setId(null);
            submission.submitted(true);
            database.addSubmission(exercise, submission, String.format("student%d", i + 1));
        }

        final var requestBody = new AtheneDTO();

        final var textBlockDTOs = textSubmissions.stream().map(textBlockService::splitSubmissionIntoBlocks).flatMap(Collection::stream).map(block -> {
            final var dto = new AtheneDTO.TextBlockDTO();
            dto.setId(block.getId());
            dto.setSubmissionId(block.getSubmission().getId());
            dto.setText(block.getText());
            dto.setStartIndex(block.getStartIndex());
            dto.setEndIndex(block.getEndIndex());
            return dto;
        }).collect(Collectors.toList());
        requestBody.setBlocks(textBlockDTOs);

        final var clusterDTOs = List.of(0, 1, 2).stream().map(cid -> {
            final var blocksInCluster = textBlockDTOs.subList(cid * 3, (cid + 1) * 3).stream().map(dto -> {
                var block = new TextBlock();
                block.setId(dto.getId());
                return block;
            }).collect(Collectors.toList());
            final double[][] matrix = { { 0.0, 0.1, 0.2 }, { 0.1, 0.0, 0.2 }, { 0.2, 0.1, 0.0 } };
            final double[] probabilities = { 0.9, 0.8, 0.7 };

            final TextCluster cluster = new TextCluster();
            cluster.setId(Long.valueOf(cid));
            cluster.setBlocks(blocksInCluster);
            cluster.setDistanceMatrix(matrix);
            cluster.setProbabilities(probabilities);
            return cluster;
        }).collect(Collectors.toMap(cluster -> cluster.getId().intValue(), cluster -> cluster));
        clusterDTOs.forEach((key, value) -> value.setId(null));

        requestBody.setClusters(clusterDTOs);

        List<Long> runningAtheneTasks = new ArrayList<>();
        runningAtheneTasks.add(exercise.getId());
        ReflectionTestUtils.setField(atheneService, "runningAtheneTasks", runningAtheneTasks);

        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", atheneApiSecret);
        request.postWithoutLocation(ATHENE_RESULT_API_PATH + exercise.getId(), requestBody, HttpStatus.OK, httpHeaders);

        final List<TextCluster> clusters = textClusterRepository.findAllByExercise(exercise);

        for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {
            TextCluster cluster = clusters.get(clusterIndex);
            assertThat(cluster.size(), equalTo(3));
            assertThat(cluster.getBlocks(), hasSize(3));

            List<TextBlock> blocks = cluster.getBlocks();
            for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
                TextBlock block = blocks.get(blockIndex);
                assertThat(block.getAddedDistance(), greaterThan(1.65));
                final TextBlock textBlockFromRequest = clusterDTOs.get(clusterIndex).getBlocks().get(blockIndex);
                assertThat(block.getId(), is(equalTo(textBlockFromRequest.getId())));
                var positionInCluster = ReflectionTestUtils.getField(block, "positionInCluster");
                assertThat(positionInCluster, is(equalTo(blockIndex)));
            }
        }

    }
}
