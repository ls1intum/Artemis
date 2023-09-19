package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TutorParticipationRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class TutorParticipationResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tutorparticipationresource";

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private TutorParticipationRepository tutorParticipationRepository;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Exercise exercise;

    private Course course1;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 5, 0, 1);
        var courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 5);
        course1 = courses.get(0);
        exercise = course1.getExercises().iterator().next();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRemoveTutorParticipationForGuidedTour() throws Exception {
        var tutorParticipations = tutorParticipationRepository.findAllByAssessedExercise_Course(course1);
        assertThat(tutorParticipations).hasSize(5);

        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        TutorParticipation tutorParticipation = tutorParticipations.get(0);
        tutorParticipation.tutor(tutor).assessedExercise(exercise);
        tutorParticipationRepository.save(tutorParticipation);

        ExampleSubmission exampleSubmission = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("", exercise, true));
        exampleSubmission.addTutorParticipations(tutorParticipationRepository.findWithEagerExampleSubmissionAndResultsByAssessedExerciseAndTutor(exercise, tutor));
        exampleSubmissionRepository.save(exampleSubmission);

        Optional<ExampleSubmission> exampleSubmissionWithEagerExercise = exampleSubmissionRepository.findWithSubmissionResultExerciseGradingCriteriaById(exampleSubmission.getId());
        if (exampleSubmissionWithEagerExercise.isPresent()) {
            exercise = exampleSubmissionWithEagerExercise.get().getExercise();
            exercise.setTitle("Patterns in Software Engineering");
            exerciseRepository.save(exercise);
        }
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.OK);
        assertThat(tutorParticipationRepository.findAllByAssessedExercise_Course(course1)).as("Removed tutor participation").hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRemoveTutorParticipationForGuidedTour_noMatchingExercise() throws Exception {
        exercise.setTitle("Patterns in Software Engineering");
        exerciseRepository.save(exercise);
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.OK);
        assertThat(tutorParticipationRepository.findAllByAssessedExercise_Course(course1)).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRemoveTutorParticipationForGuidedTour_forbidden() throws Exception {
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.FORBIDDEN);

        exercise.setTitle("Patterns in Software Engineering");
        exerciseRepository.save(exercise);
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.OK);
        assertThat(tutorParticipationRepository.findAllByAssessedExercise_Course(course1)).hasSize(4);
    }
}
