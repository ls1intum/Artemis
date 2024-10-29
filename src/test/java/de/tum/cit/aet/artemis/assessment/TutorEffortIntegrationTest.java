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

import de.tum.cit.aet.artemis.assessment.dto.TutorEffortDTO;
import de.tum.cit.aet.artemis.assessment.repository.TextAssessmentEventRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextAssessmentEvent;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TutorEffortIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tutoreffort"; // only lower case is supported

    @Autowired
    private TextSubmissionTestRepository textSubmissionTestRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

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
        textSubmission = textSubmissionTestRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(exercise.getId()).iterator().next();
        var instructor = userUtilService.createAndSaveUser(TEST_PREFIX + "instructor");
        instructor.setGroups(Set.of(course.getInstructorGroupName()));
        userTestRepository.save(instructor);
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

        List<TutorEffortDTO> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK,
                TutorEffortDTO.class);

        TutorEffortDTO effortExpected = createTutorEffortObject(0);

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

        List<TutorEffortDTO> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK,
                TutorEffortDTO.class);

        TutorEffortDTO effortExpected = createTutorEffortObject(25);

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

        List<TutorEffortDTO> tutorEfforts = request.getList("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/tutor-effort", HttpStatus.OK,
                TutorEffortDTO.class);

        TutorEffortDTO effortExpected = createTutorEffortObject(0);

        assertThat(tutorEfforts).isNotNull().hasSize(1);
        assertThat(tutorEfforts.getFirst()).usingRecursiveComparison().isEqualTo(effortExpected);
    }

    TutorEffortDTO createTutorEffortObject(int minutes) {
        return new TutorEffortDTO(1L, 1, minutes, exercise.getId(), course.getId());
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
