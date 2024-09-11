package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.assessment.repository.TextAssessmentEventRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.TextSubmission;
import de.tum.cit.aet.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.domain.statistics.tutor.effort.TutorEffort;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseUtilService;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;

class TutorEffortIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tutoreffort"; // only lower case is supported

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextAssessmentEventRepository textAssessmentEventRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course;

    private Exercise exercise;

    private TextSubmission textSubmission;

    private StudentParticipation studentParticipation;

    /**
     * Initializes the database with a course that contains a tutor and a text submission
     */
    @BeforeEach
    void initTestCase() {
        course = courseUtilService.createCourseWithTextExerciseAndTutor(TEST_PREFIX + "tutor1");
        exercise = course.getExercises().iterator().next();
        studentParticipation = studentParticipationRepository.findByExerciseId(exercise.getId()).stream().iterator().next();
        textSubmission = textSubmissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(exercise.getId()).iterator().next();
        var instructor = userUtilService.createAndSaveUser(TEST_PREFIX + "instructor");
        instructor.setGroups(Set.of(course.getInstructorGroupName()));
        userRepository.save(instructor);
    }

    /**
     * Tests the TutorEffortResource.calculateTutorEffort method with a scenario involving a distance between
     * timestamps of 1 minute but
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testCalculateTutorEfforts0MinutesOneTimestamp() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(1, 1);

        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(0);

        assertThat(tutorEfforts).isNotNull().hasSize(1);
        assertThat(tutorEfforts.getFirst()).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    /**
     * Tests the TutorEffortResource.calculateTutorEffort method with a scenario involving a distance
     * between timestamps of 5 minutes.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testCalculateTutorEffortsDistance5Minutes() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(6, 5);

        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(25);

        assertThat(tutorEfforts).isNotNull().hasSize(1);
        assertThat(tutorEfforts.getFirst()).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    /**
     * Tests the TutorEffortResource.calculateTutorEffort method with a scenario involving 10 minutes
     * of distance between timestamps. In this case time difference between timestamps is not calculated
     * as it is referred as a period of inactivity
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testCalculateTutorEffortsDistance10Minutes() throws Exception {
        List<TextAssessmentEvent> events = createTextAssessmentEventsInIntervals(11, 10);
        textAssessmentEventRepository.saveAll(events);

        List<TutorEffort> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK, TutorEffort.class);

        TutorEffort effortExpected = createTutorEffortObject(0);

        assertThat(tutorEfforts).isNotNull().hasSize(1);
        assertThat(tutorEfforts.getFirst()).usingRecursiveComparison().isEqualTo(effortExpected);
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
            TextAssessmentEvent event = textExerciseUtilService.createSingleTextAssessmentEvent(course.getId(), 1L, exercise.getId(), studentParticipation.getId(),
                    textSubmission.getId());
            event.setTimestamp(instant);
            instant = instant.plusSeconds(distance * 60L);
            events.add(event);
        }
        return events;
    }
}
