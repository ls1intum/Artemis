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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.TextAssessmentEventResource;

public class TextExerciseAnalyticsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TextAssessmentEventResource textAssessmentEventResource;

    private Course course;

    private User tutor;

    private Exercise exercise;

    @BeforeEach
    public void initTestCase() {
        course = database.createCourseWithTutor("tutor1");
        tutor = userRepository.getUserByLoginElseThrow("tutor1");
        exercise = course.getExercises().iterator().next();
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddMultipleCompleteAssessmentEvents() throws Exception {
        List<TextAssessmentEvent> events = ModelFactory.generateMultipleTextAssessmentEvents(course.getId(), tutor.getId(), exercise.getId());
        for (TextAssessmentEvent event : events) {
            ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddSingleCompleteAssessmentEvent() throws Exception {
        expectEventAddedWithResponse(HttpStatus.OK, tutor.getId());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddSingleCompleteAssessmentEvent_withTutorNotInCourse() throws Exception {
        // Tutor with tutor1 id incremented is not part of the course, therefore forbidden to access this resource
        expectEventAddedWithResponse(HttpStatus.BAD_REQUEST, tutor.getId() + 1);
    }

    private void expectEventAddedWithResponse(HttpStatus expected, Long userId) {
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), userId, exercise.getId());
        ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseEntity.getStatusCode()).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddSingleCompleteAssessmentEvent_withNotNullEventId() {
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), tutor.getId(), exercise.getId());
        event.setId(1L);
        ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetAllEventsByCourseId() {
        User user = new User();
        user.setLogin("admin");
        user.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(user);
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), user.getId(), exercise.getId());
        ResponseEntity<Void> responseAddEvent = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseAddEvent.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<TextAssessmentEvent>> responseFindEvents = textAssessmentEventResource.getEventsByCourseId(course.getId());
        assertThat(responseFindEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseFindEvents.getBody()).isEqualTo(List.of(event));
    }
}
