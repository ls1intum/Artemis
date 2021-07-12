package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @BeforeEach
    public void initTestCase() {
        course = database.createCourseWithTutor("tutor1");
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddMultipleCompleteAssessmentEvents() throws Exception {
        List<TextAssessmentEvent> events = ModelFactory.generateMultipleTextAssessmentEvents(course.getId());
        for (TextAssessmentEvent event : events) {
            ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddSingleCompleteAssessmentEvent() throws Exception {
        expectEventAddedWithResponse(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testAddSingleCompleteAssessmentEvent_withTutorNotInCourse() throws Exception {
        User user = new User();
        user.setLogin("tutor2");
        userRepository.save(user);
        // 'tutor2' is not part of the course, therefore forbidden to access this resource
        expectEventAddedWithResponse(HttpStatus.FORBIDDEN);
    }

    private void expectEventAddedWithResponse(HttpStatus expected) throws Exception {
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId());
        ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseEntity.getStatusCode()).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAddSingleCompleteAssessmentEvent_withNotNullEventId() throws Exception {
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId());
        event.setId(1L);
        ResponseEntity<Void> responseEntity = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testFindAllEvents() throws Exception {
        User user = new User();
        user.setLogin("admin");
        user.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepository.save(user);
        TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId());
        ResponseEntity<Void> responseAddEvent = textAssessmentEventResource.addAssessmentEvent(event);
        assertThat(responseAddEvent.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<TextAssessmentEvent>> responseFindEvents = textAssessmentEventResource.findAllEvents();
        assertThat(responseFindEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseFindEvents.getBody()).isEqualTo(List.of(event));
    }
}
