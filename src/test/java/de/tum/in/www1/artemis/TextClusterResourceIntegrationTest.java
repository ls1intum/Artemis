package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;
import de.tum.in.www1.artemis.web.rest.TextClusterResource;
import net.sourceforge.plantuml.Log;

public class TextClusterResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

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

        exercise = (TextExercise) course.getExercises().iterator().next();
        StudentParticipation studentParticipation = studentParticipationRepository.findAll().get(0);

        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        // textSubmission.setId(1L);
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

        List<TextCluster> cl = textClusterRepository.findAll();
        List<Result> rs = resultRepository.findAll();
        List<TextSubmission> sb = textSubmissionRepository.findAll();
        // assertThat(sb).isEqualTo(0);
        Log.debug("SSSS" + sb);
        textBlockRepository.saveAll(textBlocks);
        List<TextBlock> tbb = textBlockRepository.findAll();

        System.out.println("test");
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    /**
    * Tests adding multiple different combinations of events
    */

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddMultipleCompleteAssessmentEvents2() throws Exception {
        ResponseEntity<List<TextClusterRepository.TextClusterStats>> responseEntity = textClusterResource.getClusterStats(exercise.getId());
        List<TextClusterRepository.TextClusterStats> stats = responseEntity.getBody();
        assertThat(stats).isNotNull();
        TextClusterRepository.TextClusterStats stat = stats.get(0);
        Long clusterSize = stat.getClusterSize();
        Long automaticFeedbacksNum = stat.getNumberOfAutomaticFeedbacks();
        Boolean disabled = stat.getDisabled();

        assertThat(clusterSize).isEqualTo(3);
        assertThat(automaticFeedbacksNum).isEqualTo(0);
        assertThat(disabled).isEqualTo(true);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddMultipleCompleteAssessmentEvents() throws Exception {
        TextCluster cluster = textClusterRepository.findAll().get(0);
        cluster.setDisabled(false);
        textClusterRepository.save(cluster);
        ResponseEntity<List<TextClusterRepository.TextClusterStats>> responseEntity = textClusterResource.getClusterStats(exercise.getId());
        List<TextClusterRepository.TextClusterStats> stats = responseEntity.getBody();

        assertThat(stats).isNotNull();
        TextClusterRepository.TextClusterStats stat = stats.get(0);
        Long clusterSize = stat.getClusterSize();
        Long automaticFeedbacksNum = stat.getNumberOfAutomaticFeedbacks();
        Boolean disabled = stat.getDisabled();

        assertThat(clusterSize).isEqualTo(3);
        assertThat(automaticFeedbacksNum).isEqualTo(0);
        assertThat(disabled).isEqualTo(false);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        database.resetDatabase();
    }

}
