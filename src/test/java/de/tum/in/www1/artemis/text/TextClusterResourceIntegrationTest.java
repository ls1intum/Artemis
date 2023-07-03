package de.tum.in.www1.artemis.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.TextClusterStatisticsDTO;

class TextClusterResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "textclusterresource";

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private TextExercise exercise;

    /**
     * Initializes the database with a course that contains a tutor and a text submission
     */
    @BeforeEach
    void initTestCase() throws Error {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = courseUtilService.createCourseWithInstructorAndTextExercise(TEST_PREFIX);

        exercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        StudentParticipation studentParticipation = studentParticipationRepository.findByExerciseId(exercise.getId()).stream().iterator().next();
        studentParticipation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipationRepository.save(studentParticipation);

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textSubmission.setParticipation(studentParticipation);
        textSubmissionRepository.save(textSubmission);

        participationUtilService.addResultToSubmission(textSubmission, AssessmentType.AUTOMATIC);

        Set<TextBlock> textBlocks = Set.of(new TextBlock().text("This is Part 1,").startIndex(0).endIndex(15).automatic(),
                new TextBlock().text("and this is Part 2.").startIndex(16).endIndex(35).automatic(),
                new TextBlock().text("There is also Part 3.").startIndex(36).endIndex(57).automatic());
        int[] clusterSizes = { 3 };
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, exercise);

        clusters.get(0).setDisabled(true);
        textBlocks.forEach(block -> {
            block.setSubmission(textSubmission);
            block.setCluster(clusters.get(0));
            block.computeId();
        });

        textSubmissionRepository.save(textSubmission);
        textClusterRepository.saveAll(clusters);
        textBlockRepository.saveAll(textBlocks);
    }

    /**
     * Checks the response data from retrieving cluster statistics is returned properly
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetClusterStats_forAllValuesSet() throws Exception {
        List<TextClusterStatisticsDTO> textClusterStatistics = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatisticsDTO.class);

        assertThat(textClusterStatistics).isNotNull();

        TextClusterStatisticsDTO firstElement = textClusterStatistics.get(0);
        Long clusterSize = firstElement.getClusterSize();
        Long automaticFeedbacksNum = firstElement.getNumberOfAutomaticFeedbacks();
        boolean disabled = firstElement.isDisabled();

        assertThat(clusterSize).isEqualTo(3);
        assertThat(automaticFeedbacksNum).isZero();
        assertThat(disabled).isTrue();
    }

    /**
     * Test getting cluster statistics with pre-enabled clusters
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetClusterStats_withEnabledCluster() throws Exception {
        TextCluster cluster = textClusterRepository.findAllByExercise(exercise).stream().iterator().next();
        cluster.setDisabled(false);
        textClusterRepository.save(cluster);

        List<TextClusterStatisticsDTO> textClusterStatistics = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatisticsDTO.class);

        TextClusterStatisticsDTO stat = textClusterStatistics.get(0);
        boolean disabled = stat.isDisabled();

        assertThat(disabled).isFalse();
    }

    /**
     * Test getting cluster statistics with at least one textblock having an automatic feedback
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetClusterStats_withAddedAutoFeedback() throws Exception {
        Result result = resultRepository.findAllByExerciseId(exercise.getId()).get(0);
        TextBlock textBlock = textBlockRepository.findAllBySubmissionId(result.getSubmission().getId()).stream().iterator().next();
        Feedback feedback = new Feedback().type(FeedbackType.AUTOMATIC).detailText("feedback").result(result).reference(textBlock.getId());
        feedbackRepository.save(feedback);

        List<TextClusterStatisticsDTO> textClusterStatistics = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatisticsDTO.class);

        TextClusterStatisticsDTO stat = textClusterStatistics.get(0);
        Long automaticFeedbacksNum = stat.getNumberOfAutomaticFeedbacks();

        assertThat(automaticFeedbacksNum).isEqualTo(1);
    }

    /**
     * Test toggleClusterDisabledPredicate combined with getting cluster statistics
     * The value is toggled from first false and then to true
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testToggleClusterDisabledPredicate_withDisabledAndEnabledCluster() throws Exception {
        TextCluster cluster = textClusterRepository.findAllByExercise(exercise).get(0);
        // set predicate to false
        request.patch("/api/text-exercises/" + exercise.getId() + "/text-clusters/" + cluster.getId() + "?disabled=false", "{}", HttpStatus.OK);

        // fetch statistics
        List<TextClusterStatisticsDTO> textClusterStatisticsFalse = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatisticsDTO.class);

        assertThat(textClusterStatisticsFalse).isNotNull();
        TextClusterStatisticsDTO textClusterStatisticFalse = textClusterStatisticsFalse.get(0);
        assertThat(textClusterStatisticFalse.isDisabled()).isFalse();

        // set predicate to true
        request.patch("/api/text-exercises/" + exercise.getId() + "/text-clusters/" + cluster.getId() + "?disabled=true", "{}", HttpStatus.OK);

        // fetch statistics
        List<TextClusterStatisticsDTO> textClusterStatisticsTrue = request.getList("/api/text-exercises/" + exercise.getId() + "/cluster-statistics", HttpStatus.OK,
                TextClusterStatisticsDTO.class);

        assertThat(textClusterStatisticsTrue).isNotNull();
        TextClusterStatisticsDTO textClusterStatsTrue = textClusterStatisticsTrue.get(0);
        assertThat(textClusterStatsTrue.isDisabled()).isTrue();
    }
}
