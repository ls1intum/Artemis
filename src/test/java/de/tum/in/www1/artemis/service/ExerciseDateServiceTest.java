package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

class ExerciseDateServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseDateService exerciseDateService;

    @Autowired
    private ModelingExerciseRepository exerciseRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    private ModelingExercise exercise;

    @BeforeEach
    void init() {
        SecurityUtils.setAuthorizationObject();

        database.addUsers(3, 2, 0, 2);
        database.addCourseWithOneModelingExercise();
        exercise = exerciseRepository.findAll().get(0);

        for (int i = 1; i <= 3; ++i) {
            var submission = ModelFactory.generateModelingSubmission(String.format("model%d", i), true);
            database.addModelingSubmission(exercise, submission, "student1");
        }

        exercise = exerciseRepository.findByIdWithStudentParticipationsSubmissionsResultsElseThrow(exercise.getId());
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void latestDueDateShouldNotExistIfNoExerciseDueDate() {
        exercise.setDueDate(null);
        exercise = exerciseRepository.save(exercise);

        // in a real scenario individual due dates should never exist if the exercise has no due date
        final var participation = exercise.getStudentParticipations().stream().findAny().get();
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(2));
        participationRepository.save(participation);

        assertThat(exerciseDateService.getLatestIndividualDueDate(exercise)).isEmpty();
        assertThat(exerciseDateService.isBeforeLatestDueDate(exercise)).isTrue();
    }

    @Test
    public void latestDueDateShouldBeExerciseDueDateIfNoIndividualDueDate() {
        final var dueDate = ZonedDateTime.now().plusHours(4);
        exercise.setDueDate(dueDate);
        exercise = exerciseRepository.save(exercise);

        assertThat(exerciseDateService.getLatestIndividualDueDate(exercise).get()).isEqualToIgnoringNanos(dueDate);
        assertThat(exerciseDateService.isBeforeLatestDueDate(exercise)).isTrue();
        assertThat(exerciseDateService.isAfterLatestDueDate(exercise)).isFalse();
    }

    @Test
    public void latestDueDateShouldBeLatestIndividualDueDate() {
        final var now = ZonedDateTime.now();
        exercise.setDueDate(now.plusHours(4));
        exercise = exerciseRepository.save(exercise);

        var participation = exercise.getStudentParticipations().stream().findAny().get();
        participation.setIndividualDueDate(now.plusHours(20));
        participationRepository.save(participation);

        assertThat(exerciseDateService.getLatestIndividualDueDate(exercise).get()).isEqualToIgnoringNanos(now.plusHours(20));
    }

    @Test
    public void participationDueDateShouldBeExerciseDueDateIfNoIndividualDueDate() {
        final var now = ZonedDateTime.now();
        exercise.setDueDate(now.plusHours(4));
        exercise = exerciseRepository.save(exercise);

        final var participation = exercise.getStudentParticipations().stream().findAny().get();
        assertThat(exerciseDateService.getDueDate(participation).get()).isEqualToIgnoringNanos(now.plusHours(4));
    }

    @Test
    public void participationDueDateShouldBeIndividualDueDate() {
        final var now = ZonedDateTime.now();
        exercise.setDueDate(now.plusHours(4));
        exercise = exerciseRepository.save(exercise);

        var participation = exercise.getStudentParticipations().stream().findAny().get();
        participation.setIndividualDueDate(now.plusHours(20));
        participation = participationRepository.save(participation);

        assertThat(exerciseDateService.getDueDate(participation).get()).isEqualToIgnoringNanos(now.plusHours(20));
    }

    @Test
    public void nowShouldBeBeforeADueDateInTheFuture() {
        final var now = ZonedDateTime.now();
        var participation = exercise.getStudentParticipations().stream().findAny().get();
        participation.setIndividualDueDate(now.plusHours(20));
        participation = participationRepository.save(participation);

        assertThat(exerciseDateService.isBeforeDueDate(participation)).isTrue();
        assertThat(exerciseDateService.isAfterDueDate(participation)).isFalse();
    }

    @Test
    public void itShouldAlwaysBeBeforeANonExistingDueDate() {
        exercise.setDueDate(null);
        exercise = exerciseRepository.save(exercise);
        var participation = exercise.getStudentParticipations().stream().findAny().get();

        assertThat(participation.getIndividualDueDate()).isNull();
        assertThat(exerciseDateService.isBeforeDueDate(participation)).isTrue();
        assertThat(exerciseDateService.isAfterDueDate(participation)).isFalse();
    }
}
