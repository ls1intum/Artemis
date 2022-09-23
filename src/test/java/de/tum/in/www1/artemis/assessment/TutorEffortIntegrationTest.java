package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.statistics.tutor.effort.TutorEffort;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

class TutorEffortIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextAssessmentEventRepository textAssessmentEventRepository;

    private Course course;

    private Exercise exercise;

    private TextSubmission textSubmission;

    private StudentParticipation studentParticipation;

    /**
     * Initializes the database with a course that contains a tutor and a text submission
     */
    @BeforeEach
    void initTestCase() {
        course = database.createCourseWithTutor("tutor1");
        exercise = course.getExercises().iterator().next();
        studentParticipation = studentParticipationRepository.findByExerciseIdWithEagerSubmissionsResultAssessor(exercise.getId()).get(0);
        textSubmission = (TextSubmission) studentParticipation.findLatestSubmission().get();
        var instructor = database.createAndSaveUser("instructor");
        instructor.setGroups(Set.of(course.getInstructorGroupName()));
        userRepository.save(instructor);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Tests the TutorEffortResource.calculateTutorEffort method with a scenario involving a distance between
     * timestamps of 1 minute but
     */
    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void testCalculateTutorEfforts0MinutesOneTimestamp() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(1, 1);

        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(0);

        assertThat(tutorEfforts).isNotNull().hasSize(1);
        assertThat(tutorEfforts.get(0)).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    /**
     * Tests the TutorEffortResource.calculateTutorEffort method with a scenario involving a distance
     * between timestamps of 5 minutes.
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void testCalculateTutorEffortsDistance5Minutes() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(6, 5);

        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(25);

        assertThat(tutorEfforts).isNotNull().hasSize(1);
        assertThat(tutorEfforts.get(0)).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    /**
     * Tests the TutorEffortResource.calculateTutorEffort method with a scenario involving 10 minutes
     * of distance between timestamps. In this case time difference between timestamps is not calculated
     * as it is referred as a period of inactivity
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    void testCalculateTutorEffortsDistance10Minutes() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(11, 10);
        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(0);

        assertThat(tutorEfforts).isNotNull().hasSize(1);
        assertThat(tutorEfforts.get(0)).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    TutorEffort createTutorEffortObject(int minutes) {
        TutorEffort tutorEffort = new TutorEffort();
        tutorEffort.setUserId(1L);
        tutorEffort.setNumberOfSubmissionsAssessed(1);
        tutorEffort.setExerciseId(exercise.getId());
        tutorEffort.setCourseId(course.getId());
        tutorEffort.setTotalTimeSpentMinutes(minutes);
        return tutorEffort;
    }

    List<TextAssessmentEvent> createTextAssessmentEventsInIntervals(int timestamps, int distance) {
        List<TextAssessmentEvent> events = new ArrayList<>();
        Instant instant = Instant.now();
        for (int i = 0; i < timestamps; i++) {
            TextAssessmentEvent event = database.createSingleTextAssessmentEvent(course.getId(), 1L, exercise.getId(), studentParticipation.getId(), textSubmission.getId());
            event.setTimestamp(instant);
            instant = instant.plusSeconds(distance * 60L);
            events.add(event);
        }
        return events;
    }
}
