package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

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

    private Course course;

    // private User tutor;

    private Exercise exercise;

    // private TextSubmission textSubmission;

    private StudentParticipation studentParticipation;

    /**
     * Initializes the database with a course that contains a tutor and a text submission
     */
    @BeforeEach
    public void initTestCase() throws Error {
        course = database.createCourseWithInstructorAndTextExercise("instructor1");

        exercise = (TextExercise) course.getExercises().iterator().next();
        studentParticipation = studentParticipationRepository.findAll().get(0);
        // textSubmission = textSubmissionRepository.findAll().get(0);

        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textSubmission.setId(1L);
        textSubmissionRepository.save(textSubmission);

        database.addResultToSubmission(textSubmission, AssessmentType.AUTOMATIC);
        // database.getUserByLogin("asd");
        // textClusterResource.
        // TextCluster cluster = new Cl
        // textClusterRepository.
        Set<TextBlock> textBlocks = Set.of(new TextBlock().text("This is Part 1,").startIndex(0).endIndex(15).automatic().id("0"),
                new TextBlock().text("and this is Part 2.").startIndex(16).endIndex(35).automatic().id("1"),
                new TextBlock().text("There is also Part 3.").startIndex(36).endIndex(57).automatic().id("2"));
        int[] clusterSizes = { 3 };
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, (TextExercise) exercise);

        double[][] minimalDistanceMatrix = new double[3][3];
        // Fill each row with an arbitrary fixed value < 1 to stimulate a simple case of 10 automatic feedback suggestions
        for (double[] row : minimalDistanceMatrix) {
            Arrays.fill(row, 0.1);
        }
        clusters.get(0).blocks(textBlocks.stream().toList()).distanceMatrix(minimalDistanceMatrix);
        textSubmission = database.addAndSaveTextBlocksToTextSubmission(textBlocks, textSubmission);
        textSubmission.setParticipation(studentParticipation);
        textSubmissionRepository.save(textSubmission);

        textClusterRepository.saveAll(clusters);
        List<TextCluster> cl = textClusterRepository.findAll();
        List<TextBlock> tbb = textBlockRepository.findAll();
        List<Result> rs = resultRepository.findAll();
        List<TextSubmission> sb = textSubmissionRepository.findAll();
        // textBlockRepository.saveAll(textBlocks);
        // try {

        // } catch (Error error) {
        System.out.println("Error");
        // }

    }

    // @AfterEach
    // public void resetDatabase() {
    // database.resetDatabase();
    // }
    //
    // /**
    // * Tests adding multiple different combinations of events
    // */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAddMultipleCompleteAssessmentEvents() throws Exception {
        ResponseEntity<List<TextClusterRepository.TextClusterStats>> responseEntity = textClusterResource.getClusterStats(exercise.getId());
        assertThat(responseEntity.getBody()).isNull();
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("test");
        assertThat(2).isEqualTo(2);
        // List<TextAssessmentEvent> events = ModelFactory.generateMultipleTextAssessmentEvents(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(),
        // textSubmission.getId());
        // for (TextAssessmentEvent event : events) {
        // ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        // assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        // }
    }
    //
    // /**
    // * Tests addition of a single assessment event
    // */
    // @Test
    // @WithMockUser(username = "tutor1", roles = "TA")
    // public void testAddSingleCompleteAssessmentEvent() {
    // expectEventAddedWithResponse(HttpStatus.OK, tutor.getId());
    // }
    //
    // /**
    // * Tests addition of a single event with a tutor that is not part of the course
    // */
    // @Test
    // @WithMockUser(username = "tutor1", roles = "TA")
    // public void testAddSingleCompleteAssessmentEvent_withTutorNotInCourse() {
    // // Tutor with tutor1 id incremented is not part of the course, therefore forbidden to access this resource
    // expectEventAddedWithResponse(HttpStatus.BAD_REQUEST, tutor.getId() + 1);
    // }
    //
    // /**
    // * Local helper function that given a userId and an expected status, adds an event and checks if the result is as expected
    // * @param expected the status expected from the Http response
    // * @param userId the id of the user to be tested in the event
    // */
    // private void expectEventAddedWithResponse(HttpStatus expected, Long userId) {
    // TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), userId, exercise.getId(), studentParticipation.getId(), textSubmission.getId());
    // ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
    // assertThat(responseEntity.getStatusCode()).isEqualTo(expected);
    // }
    //
    // /**
    // * Tests addition of a single event with event id not null. The event is auto generated in the server side,
    // * that is why we treat incoming events with an already generated id as badly formed requests
    // */
    // @Test
    // @WithMockUser(username = "tutor1", roles = "TA")
    // public void testAddSingleCompleteAssessmentEvent_withNotNullEventId() {
    // TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());
    // event.setId(1L);
    // ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
    // assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    // }
    //
    // /**
    // * Tests the get events endpoint with admin role
    // */
    // @Test
    // @WithMockUser(username = "admin", roles = "ADMIN")
    // public void testGetAllEventsByCourseId() {
    // User user = new User();
    // user.setLogin("admin");
    // user.setGroups(Set.of(course.getTeachingAssistantGroupName()));
    // userRepository.save(user);
    // TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), user.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());
    // ResponseEntity<Void> responseAddEvent = textAssessmentEventResource.addAssessmentEvent(event);
    // assertThat(responseAddEvent.getStatusCode()).isEqualTo(HttpStatus.OK);
    //
    // ResponseEntity<List<TextAssessmentEvent>> responseFindEvents = textAssessmentEventResource.getEventsByCourseId(course.getId());
    // assertThat(responseFindEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
    // assertThat(responseFindEvents.getBody()).isEqualTo(List.of(event));
    // }
}
