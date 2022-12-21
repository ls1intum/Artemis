package de.tum.in.www1.artemis.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

<<<<<<<< HEAD:src/test/java/de/tum/in/www1/artemis/text/TextExerciseAnalyticsIntegrationTest.java
import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
========
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
>>>>>>>> develop:src/test/java/de/tum/in/www1/artemis/TextExerciseAssessmentEventIntegrationTest.java
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

class TextExerciseAssessmentEventIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "textexerciseanalytics";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextAssessmentEventRepository textAssessmentEventRepository;

    private Course course;

    private User tutor;

    private Exercise exercise;

    private TextSubmission textSubmission;

    private StudentParticipation studentParticipation;

    /**
     * Initializes the database with a course that contains a tutor and a text submission
     */
    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 0, 0, 1, 1);
        course = database.createCourseWithTutor(TEST_PREFIX + "tutor1");
        tutor = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "tutor1");
        exercise = course.getExercises().iterator().next();
        studentParticipation = studentParticipationRepository.findByExerciseId(exercise.getId()).stream().iterator().next();
        textSubmission = textSubmissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(exercise.getId()).get(0);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Tests adding multiple different combinations of events
     */
    @Test
<<<<<<<< HEAD:src/test/java/de/tum/in/www1/artemis/text/TextExerciseAnalyticsIntegrationTest.java
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAddMultipleCompleteAssessmentEvents() {
========
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddMultipleCompleteAssessmentEvents() throws Exception {
>>>>>>>> develop:src/test/java/de/tum/in/www1/artemis/TextExerciseAssessmentEventIntegrationTest.java
        List<TextAssessmentEvent> events = ModelFactory.generateMultipleTextAssessmentEvents(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(),
                textSubmission.getId());
        for (TextAssessmentEvent event : events) {
            request.post("/api/event-insights/text-assessment/events", event, HttpStatus.CREATED);
        }
    }

    /**
     * Tests addition of a single assessment event
     */
    @Test
<<<<<<<< HEAD:src/test/java/de/tum/in/www1/artemis/text/TextExerciseAnalyticsIntegrationTest.java
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent() {
        expectEventAddedWithResponse(HttpStatus.OK, tutor.getId());
========
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent() throws Exception {
        expectEventAddedWithResponse(HttpStatus.CREATED, tutor.getId());
>>>>>>>> develop:src/test/java/de/tum/in/www1/artemis/TextExerciseAssessmentEventIntegrationTest.java
    }

    /**
     * Tests addition of a single event with a tutor that is not part of the course
     */
    @Test
<<<<<<<< HEAD:src/test/java/de/tum/in/www1/artemis/text/TextExerciseAnalyticsIntegrationTest.java
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withTutorNotInCourse() {
========
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withTutorNotInCourse() throws Exception {
>>>>>>>> develop:src/test/java/de/tum/in/www1/artemis/TextExerciseAssessmentEventIntegrationTest.java
        // Tutor with tutor1 id incremented is not part of the course, therefore forbidden to access this resource
        expectEventAddedWithResponse(HttpStatus.BAD_REQUEST, tutor.getId() + 1);
    }

    /**
     * Local helper function that given a userId and an expected status, adds an event and checks if the result is as expected
     * @param expected the status expected from the Http response
     * @param userId the id of the user to be tested in the event
     */
    private void expectEventAddedWithResponse(HttpStatus expected, Long userId) throws Exception {
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), userId, exercise.getId(), studentParticipation.getId(), textSubmission.getId());

        request.post("/api/event-insights/text-assessment/events", event, expected);
    }

    /**
     * Tests addition of a single event with event id not null. The event is auto generated in the server side,
     * that is why we treat incoming events with an already generated id as badly formed requests
     */
    @Test
<<<<<<<< HEAD:src/test/java/de/tum/in/www1/artemis/text/TextExerciseAnalyticsIntegrationTest.java
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withNotNullEventId() {
========
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withNotNullEventId() throws Exception {
>>>>>>>> develop:src/test/java/de/tum/in/www1/artemis/TextExerciseAssessmentEventIntegrationTest.java
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        event.setId(1L);
        request.post("/api/event-insights/text-assessment/events", event, HttpStatus.BAD_REQUEST);
    }

    @Test
<<<<<<<< HEAD:src/test/java/de/tum/in/www1/artemis/text/TextExerciseAnalyticsIntegrationTest.java
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withExampleSubmission() {
========
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withExampleSubmission() throws Exception {
>>>>>>>> develop:src/test/java/de/tum/in/www1/artemis/TextExerciseAssessmentEventIntegrationTest.java
        textSubmission.setExampleSubmission(true);
        textSubmissionRepository.saveAndFlush(textSubmission);
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        request.post("/api/event-insights/text-assessment/events", event, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests the get events endpoint with admin role
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
<<<<<<<< HEAD:src/test/java/de/tum/in/www1/artemis/text/TextExerciseAnalyticsIntegrationTest.java
    void testGetAllEventsByCourseId() {
        User user = database.getUserByLogin("admin");
========
    void testGetAllEventsByCourseId() throws Exception {
        User user = new User();
        user.setLogin("admin");
        user.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(user);
>>>>>>>> develop:src/test/java/de/tum/in/www1/artemis/TextExerciseAssessmentEventIntegrationTest.java
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), user.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());

        request.post("/api/event-insights/text-assessment/events", event, HttpStatus.CREATED);

        var foundEvents = request.getList("/api/admin/event-insights/text-assessment/events/" + course.getId(), HttpStatus.OK, TextAssessmentEvent.class);
        assertThat(foundEvents).hasSize(1);
        TextAssessmentEvent foundEvent = foundEvents.get(0);
        assertThat(foundEvent.getId()).isNotNull();
        assertThat(foundEvent.getCourseId()).isEqualTo(course.getId());
        assertThat(foundEvent.getUserId()).isEqualTo(user.getId());
        assertThat(foundEvent.getTextExerciseId()).isEqualTo(exercise.getId());
        assertThat(foundEvent.getParticipationId()).isEqualTo(studentParticipation.getId());
        assertThat(foundEvent.getSubmissionId()).isEqualTo(textSubmission.getId());
    }

    /**
     * Tests the get events endpoint with admin role
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetNumberOfTutorsInvolvedInAssessingByExerciseAndCourseId() throws Exception {
        User user = database.getUserByLogin(TEST_PREFIX + "instructor1");

        TextAssessmentEvent event1 = database.createSingleTextAssessmentEvent(course.getId(), 0L, exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        TextAssessmentEvent event2 = database.createSingleTextAssessmentEvent(course.getId(), 1L, exercise.getId(), studentParticipation.getId(), textSubmission.getId());

        // Add two events with two different tutor ids
        textAssessmentEventRepository.saveAll(List.of(event1, event2));

        int numberOfTutorsInvolved = request.get("/api/event-insights/text-assessment/courses/" + course.getId() + "/text-exercises/" + exercise.getId() + "/tutors-involved",
                HttpStatus.OK, Integer.class);

        assertThat(numberOfTutorsInvolved).isNotNull().isEqualTo(2);
    }
}
