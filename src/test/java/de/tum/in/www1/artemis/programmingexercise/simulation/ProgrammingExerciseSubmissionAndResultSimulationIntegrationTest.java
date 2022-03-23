package de.tum.in.www1.artemis.programmingexercise.simulation;

import static de.tum.in.www1.artemis.web.rest.ProgrammingSubmissionResultSimulationResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringDevelopmentTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.*;

public class ProgrammingExerciseSubmissionAndResultSimulationIntegrationTest extends AbstractSpringDevelopmentTest {

    @Autowired
    ProgrammingSubmissionRepository submissionRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ResultRepository resultRepository;

    private Long exerciseId;

    @BeforeEach
    void setUp() {
        database.addUsers(3, 2, 1, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        ProgrammingExercise exercise = programmingExerciseRepository.findAllWithEagerParticipationsAndLegalSubmissions().get(0);
        database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        database.addStudentParticipationForProgrammingExercise(exercise, "student2");

        exerciseId = exercise.getId();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    /**
     * This tests if the submission is created for programming exercises without local setup
     */
    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void shouldCreateSubmissionWithoutConnectionToVCSandCI() throws Exception {
        assertThat(submissionRepository.findAll()).isEmpty();
        final ProgrammingSubmission returnedSubmission = request.postWithResponseBody(ROOT + SUBMISSIONS_SIMULATION.replace("{exerciseId}", String.valueOf(exerciseId)), null,
                ProgrammingSubmission.class, HttpStatus.CREATED);
        assertThat(submissionRepository.findAll()).hasSize(1);

        ProgrammingSubmission submission = submissionRepository.findAll().get(0);
        assertThat(returnedSubmission).isEqualTo(submission);
        assertThat(participationRepository.findById(submission.getParticipation().getId())).isPresent();
        assertThat(submission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(submission.isSubmitted()).isTrue();
    }

    // The tutor has the editor role, but does not have the permissions for the repository since the tutor is not part of the editor group.
    @Test
    @WithMockUser(username = "tutor1", roles = "EDITOR")
    void shouldCreateSubmissionWithoutConnectionToVCSandCI_forbidden() throws Exception {
        request.post(ROOT + SUBMISSIONS_SIMULATION.replace("{exerciseId}", String.valueOf(exerciseId)), null, HttpStatus.FORBIDDEN);
    }

    /**
     * This tests if the result is created for programming exercises without local setup
     */
    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void shouldCreateResultWithoutConnectionToVCSandCI() throws Exception {
        final ProgrammingSubmission returnedSubmission = request.postWithResponseBody(ROOT + SUBMISSIONS_SIMULATION.replace("{exerciseId}", String.valueOf(exerciseId)), null,
                ProgrammingSubmission.class, HttpStatus.CREATED);
        assertThat(resultRepository.findAll()).isEmpty();
        Result returnedResult = request.postWithResponseBody(ROOT + RESULTS_SIMULATION.replace("{exerciseId}", String.valueOf(exerciseId)), null, Result.class, HttpStatus.CREATED);
        ProgrammingSubmission submission = submissionRepository.findAll().get(0);
        assertThat(returnedSubmission).isEqualTo(submission);
        assertThat(resultRepository.findAll()).hasSize(1);
        Result result = resultRepository.findAll().get(0);
        assertThat(returnedResult).isEqualTo(result);
        assertThat(result.getParticipation().getId()).isEqualTo(submission.getParticipation().getId());
    }

    // The tutor has the editor role, but does not have the permissions for the repository since the tutor is not part of the editor group.
    @Test
    @WithMockUser(username = "tutor1", roles = "EDITOR")
    void shouldCreateResultWithoutConnectionToVCSandCI_forbidden() throws Exception {
        request.post(ROOT + RESULTS_SIMULATION.replace("{exerciseId}", String.valueOf(exerciseId)), null, HttpStatus.NOT_FOUND);
    }
}
