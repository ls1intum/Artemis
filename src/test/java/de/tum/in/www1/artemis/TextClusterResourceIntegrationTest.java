package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;
import de.tum.in.www1.artemis.web.rest.TextClusterResource;

public class TextClusterResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextClusterResource textClusterResource;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private ResultRepository resultRepository;

    private Exercise exercise;

    /**
     * Initializes the database with a course that contains a tutor and a text submission
     */
    @BeforeEach
    public void initTestCase() throws Error {
        Course course = database.createCourseWithInstructorAndTextExercise("instructor1");

        exercise = course.getExercises().iterator().next();
        StudentParticipation studentParticipation = studentParticipationRepository.findAll().get(0);

        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textSubmissionRepository.save(textSubmission);

        database.addResultToSubmission(textSubmission, AssessmentType.AUTOMATIC);

        Set<TextBlock> textBlocks = Set.of(new TextBlock().text("This is Part 1,").startIndex(0).endIndex(15).automatic(),
                new TextBlock().text("and this is Part 2.").startIndex(16).endIndex(35).automatic(),
                new TextBlock().text("There is also Part 3.").startIndex(36).endIndex(57).automatic());
        int[] clusterSizes = { 3 };
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, (TextExercise) exercise);

        clusters.get(0).setDisabled(true);
        textBlocks.forEach(block -> {
            block.setSubmission(textSubmission);
            block.setCluster(clusters.get(0));
            block.computeId();
        });

        textSubmission.setParticipation(studentParticipation);
        textSubmissionRepository.save(textSubmission);
        textClusterRepository.saveAll(clusters);
        textBlockRepository.saveAll(textBlocks);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    /**
    * Checks the response data from retrieving cluster statistics is returned properly
    */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetClusterStats_forAllValuesSet() throws Exception {
        List<TextClusterStatistics> textClusterStatistics = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatistics.class);

        assertThat(textClusterStatistics).isNotNull();

        TextClusterStatistics firstElement = textClusterStatistics.get(0);
        Long clusterSize = firstElement.getClusterSize();
        Long automaticFeedbacksNum = firstElement.getNumberOfAutomaticFeedbacks();
        boolean disabled = firstElement.getDisabled();

        assertThat(clusterSize).isEqualTo(3);
        assertThat(automaticFeedbacksNum).isEqualTo(0);
        assertThat(disabled).isEqualTo(true);
    }

    /**
     * Test getting cluster statistics with pre-enabled clusters
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetClusterStats_withEnabledCluster() throws Exception {
        TextCluster cluster = textClusterRepository.findAll().get(0);
        cluster.setDisabled(false);
        textClusterRepository.save(cluster);

        List<TextClusterStatistics> textClusterStatistics = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatistics.class);

        TextClusterStatistics stat = textClusterStatistics.get(0);
        boolean disabled = stat.getDisabled();

        assertThat(disabled).isEqualTo(false);
    }

    /**
     * Test getting cluster statistics with at least one textblock having an automatic feedback
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetClusterStats_withAddedAutoFeedback() throws Exception {
        Result result = resultRepository.findAll().get(0);
        TextBlock textBlock = textBlockRepository.findAll().get(0);
        Feedback feedback = new Feedback().type(FeedbackType.AUTOMATIC).detailText("feedback").result(result).reference(textBlock.getId());
        feedbackRepository.save(feedback);

        List<TextClusterStatistics> textClusterStatistics = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatistics.class);

        TextClusterStatistics stat = textClusterStatistics.get(0);
        Long automaticFeedbacksNum = stat.getNumberOfAutomaticFeedbacks();

        assertThat(automaticFeedbacksNum).isEqualTo(1);
    }

    /**
     * Test toggleClusterDisabledPredicate combined with getting cluster statistics
     * The value is toggled from first false and then to true
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testToggleClusterDisabledPredicate_withDisabledAndEnabledCluster() throws Exception {
        TextCluster cluster = textClusterRepository.findAll().get(0);
        // set predicate to false
        request.put("/text-clusters/" + cluster.getId() + "?disabled=false", Void.class, HttpStatus.OK);

        // ResponseEntity<Void> toggleResponseFalse = textClusterResource.toggleClusterDisabledPredicate(cluster.getId(), false);
        // assertThat(toggleResponseFalse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // fetch statistics
        List<TextClusterStatistics> textClusterStatisticsFalse = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatistics.class);

        assertThat(textClusterStatisticsFalse).isNotNull();
        TextClusterStatistics textClusterStatisticFalse = textClusterStatisticsFalse.get(0);
        assertThat(textClusterStatisticFalse.getDisabled()).isEqualTo(false);

        // set predicate to true
        request.put("/text-clusters/" + cluster.getId() + "?disabled=true", Void.class, HttpStatus.OK);
        // ResponseEntity<Void> toggleResponseTrue = textClusterResource.toggleClusterDisabledPredicate(cluster.getId(), true);
        // assertThat(toggleResponseTrue.getStatusCode()).isEqualTo(HttpStatus.OK);

        // fetch statistics
        List<TextClusterStatistics> textClusterStatisticsTrue = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatistics.class);

        assertThat(textClusterStatisticsTrue).isNotNull();
        TextClusterStatistics textClusterStatsTrue = textClusterStatisticsTrue.get(0);
        assertThat(textClusterStatsTrue.getDisabled()).isEqualTo(true);
    }
}
