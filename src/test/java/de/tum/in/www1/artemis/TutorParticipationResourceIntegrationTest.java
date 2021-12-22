package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TutorParticipationRepository;

public class TutorParticipationResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private TutorParticipationRepository tutorParticipationRepository;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepository;

    private Exercise exercise;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 5, 0, 1);
        database.createCoursesWithExercisesAndLectures(true);
        exercise = exerciseRepository.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testRemoveTutorParticipationForGuidedTour() throws Exception {
        assertThat(tutorParticipationRepository.findAll().size()).isEqualTo(5);

        User tutor = database.getUserByLogin("tutor1");
        TutorParticipation tutorParticipation = tutorParticipationRepository.findAll().get(0);
        tutorParticipation.tutor(tutor).assessedExercise(exercise);
        tutorParticipationRepository.save(tutorParticipation);

        ExampleSubmission exampleSubmission = database.addExampleSubmission(database.generateExampleSubmission("", exercise, true));
        exampleSubmission.addTutorParticipations(tutorParticipationRepository.findWithEagerExampleSubmissionAndResultsByAssessedExerciseAndTutor(exercise, tutor));
        exampleSubmissionRepository.save(exampleSubmission);

        Optional<ExampleSubmission> exampleSubmissionWithEagerExercise = exampleSubmissionRepository.findWithSubmissionResultExerciseGradingCriteriaById(exampleSubmission.getId());
        if (exampleSubmissionWithEagerExercise.isPresent()) {
            exercise = exampleSubmissionWithEagerExercise.get().getExercise();
            exercise.setTitle("Patterns in Software Engineering");
            exerciseRepository.save(exercise);
        }
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.OK);
        assertThat(tutorParticipationRepository.findAll().size()).as("Removed tutor participation").isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testRemoveTutorParticipationForGuidedTour_noMatchingExercise() throws Exception {
        exercise.setTitle("Patterns in Software Engineering");
        exerciseRepository.save(exercise);
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.OK);
        assertThat(tutorParticipationRepository.findAll().size()).as("Does not remove tutor participation with wrong assessedExercise").isEqualTo(5);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testRemoveTutorParticipationForGuidedTour_forbidden() throws Exception {
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.FORBIDDEN);

        exercise.setTitle("Patterns in Software Engineering");
        exerciseRepository.save(exercise);
        request.delete("/api/guided-tour/exercises/" + exercise.getId() + "/example-submission", HttpStatus.OK);
        assertThat(tutorParticipationRepository.findAll().size()).as("Does not remove tutor participation with wrong assessedExercise").isEqualTo(5);
    }
}
