package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.statistics.tutor.effort.TutorEffort;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentEventRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

public class TutorEffortIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

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
    public void initTestCase() {
        course = database.createCourseWithTutor("tutor1");
        exercise = course.getExercises().iterator().next();
        studentParticipation = studentParticipationRepository.findAll().get(0);
        textSubmission = (TextSubmission) textSubmissionRepository.findAll().get(0);
        User user = new User();
        user.setLogin("instructor");
        user.setGroups(Set.of(course.getInstructorGroupName()));
        userRepository.save(user);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    public void testCalculateTutorEfforts() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(11, 1);

        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(1L, 10, 1);

        assertThat(tutorEfforts).isNotNull();
        assertThat(tutorEfforts.size()).isEqualTo(1);
        assertThat(tutorEfforts.get(0)).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    public void testCalculateTutorEffortsDistance5Minutes() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(5, 5);

        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(1L, 20, 1);

        assertThat(tutorEfforts).isNotNull();
        assertThat(tutorEfforts.size()).isEqualTo(1);
        assertThat(tutorEfforts.get(0)).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    @Test
    @WithMockUser(username = "instructor", roles = "INSTRUCTOR")
    public void testCalculateTutorEffortsDistance10Minutes() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(11, 10);
        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(1L, 0, 1);

        assertThat(tutorEfforts).isNotNull();
        assertThat(tutorEfforts.size()).isEqualTo(1);
        assertThat(tutorEfforts.get(0)).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    TutorEffort createTutorEffortObject(long userId, int minutes, int numSubmissions) {
        TutorEffort tutorEffort = new TutorEffort();
        tutorEffort.setUserId(userId);
        tutorEffort.setExerciseId(exercise.getId());
        tutorEffort.setCourseId(course.getId());
        tutorEffort.setTotalTimeSpentMinutes(minutes);
        tutorEffort.setNumberOfSubmissionsAssessed(numSubmissions);
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
