package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.TextAssessmentEventResource;

class TextExerciseAnalyticsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextAssessmentEventResource textAssessmentEventResource;

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
        course = database.createCourseWithTutor("tutor1");
        tutor = userRepository.getUserByLoginElseThrow("tutor1");
        exercise = course.getExercises().iterator().next();
        studentParticipation = studentParticipationRepository.findAll().get(0);
        textSubmission = textSubmissionRepository.findAll().get(0);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Tests adding multiple different combinations of events
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddMultipleCompleteAssessmentEvents() {
        List<TextAssessmentEvent> events = ModelFactory.generateMultipleTextAssessmentEvents(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(),
                textSubmission.getId());
        for (TextAssessmentEvent event : events) {
            ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    /**
     * Tests addition of a single assessment event
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent() {
        expectEventAddedWithResponse(HttpStatus.OK, tutor.getId());
    }

    /**
     * Tests addition of a single event with a tutor that is not part of the course
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withTutorNotInCourse() {
        // Tutor with tutor1 id incremented is not part of the course, therefore forbidden to access this resource
        expectEventAddedWithResponse(HttpStatus.BAD_REQUEST, tutor.getId() + 1);
    }

    /**
     * Local helper function that given a userId and an expected status, adds an event and checks if the result is as expected
     * @param expected the status expected from the Http response
     * @param userId the id of the user to be tested in the event
     */
    private void expectEventAddedWithResponse(HttpStatus expected, Long userId) {
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), userId, exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseEntity.getStatusCode()).isEqualTo(expected);
    }

    /**
     * Tests addition of a single event with event id not null. The event is auto generated in the server side,
     * that is why we treat incoming events with an already generated id as badly formed requests
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withNotNullEventId() {
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        event.setId(1L);
        ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAddSingleCompleteAssessmentEvent_withExampleSubmission() {
        textSubmission.setExampleSubmission(true);
        textSubmissionRepository.saveAndFlush(textSubmission);
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), tutor.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests the get events endpoint with admin role
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllEventsByCourseId() {
        User user = new User();
        user.setLogin("admin");
        user.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(user);
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), user.getId(), exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        ResponseEntity<Void> responseAddEvent = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseAddEvent.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<TextAssessmentEvent>> responseFindEvents = textAssessmentEventResource.getEventsByCourseId(course.getId());
        assertThat(responseFindEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseFindEvents.getBody()).isEqualTo(List.of(event));
    }

    /**
     * Tests the get events endpoint with admin role
     */
    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void testGetNumberOfTutorsInvolvedInAssessingByExerciseAndCourseId() throws Exception {
        User user = new User();
        user.setLogin("instructor");
        user.setGroups(Set.of(course.getInstructorGroupName()));
        userRepository.save(user);

        TextAssessmentEvent event1 = database.createSingleTextAssessmentEvent(course.getId(), 0L, exercise.getId(), studentParticipation.getId(), textSubmission.getId());
        TextAssessmentEvent event2 = database.createSingleTextAssessmentEvent(course.getId(), 1L, exercise.getId(), studentParticipation.getId(), textSubmission.getId());

        // Add two events with two different tutor ids
        textAssessmentEventRepository.saveAll(List.of(event1, event2));

        int numberOfTutorsInvolved = request.get("/api/event-insights/text-assessment/courses/" + course.getId() + "/text-exercises/" + exercise.getId() + "/tutors-involved",
                HttpStatus.OK, Integer.class);

        assertThat(numberOfTutorsInvolved).isNotNull().isEqualTo(2);
    }
}
