package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TutorParticipationResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tutorparticipationresource";

    @Autowired
    private TutorParticipationTestRepository tutorParticipationRepository;

    @Autowired
    private ExampleSubmissionTestRepository exampleSubmissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Exercise exercise;

    private Course course1;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 5, 0, 1);
        var courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 5);
        course1 = courses.getFirst();
        exercise = course1.getExercises().iterator().next();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRemoveTutorParticipationForGuidedTour() throws Exception {
        var tutorParticipations = tutorParticipationRepository.findAllByAssessedExercise_Course(course1);
        assertThat(tutorParticipations).hasSize(5);

        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        TutorParticipation tutorParticipation = tutorParticipations.getFirst();
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
