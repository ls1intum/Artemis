package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseService;

class ProgrammingExerciseServiceTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "progexservice";

    @Autowired
    private ProgrammingExerciseService programmingExerciseService;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private ProgrammingExercise programmingExercise1;

    private ProgrammingExercise programmingExercise2;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 2);
        var course1 = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var course2 = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        programmingExercise1 = ExerciseUtilService.getFirstExerciseWithType(course1, ProgrammingExercise.class);
        programmingExercise2 = ExerciseUtilService.getFirstExerciseWithType(course2, ProgrammingExercise.class);

        programmingExercise1.setReleaseDate(null);
        programmingExercise2.setReleaseDate(null);
        programmingExercise1 = programmingExerciseRepository.save(programmingExercise1);
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldFindProgrammingExerciseWithBuildAndTestDateInFuture() {
        programmingExercise1.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise1);
        programmingExercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise2);

        List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAllWithBuildAndTestAfterDueDateInFuture();
        assertThat(programmingExercises).contains(programmingExercise1).doesNotContain(programmingExercise2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void findByIdWithTemplateAndSolutionParticipationAndAuxiliaryReposAndLatestResultFeedbackTestCasesElseThrow_shouldContainResults() {
        programmingExercise1 = programmingExerciseRepository.findWithTemplateParticipationAndLatestSubmissionByIdElseThrow(programmingExercise1.getId());
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise1.getTemplateParticipation();
        Submission templateSubmission = participationUtilService.addSubmission(templateParticipation, new ProgrammingSubmission());
        participationUtilService.addResultToSubmission(null, null, templateSubmission);

        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findWithLatestSubmissionByExerciseIdElseThrow(programmingExercise1.getId());
        // this is a submission without results
        participationUtilService.addSubmission(solutionParticipation, new ProgrammingSubmission());
        programmingExerciseRepository.save(programmingExercise1);

        ProgrammingExercise fetchedExercise = programmingExerciseService
                .findByIdWithTemplateAndSolutionParticipationAndAuxiliaryReposAndLatestResultFeedbackTestCasesElseThrow(programmingExercise1.getId());

        assertThat(fetchedExercise).isNotNull();

        assertThat(fetchedExercise.getTemplateParticipation()).isNotNull();
        assertThat(fetchedExercise.getTemplateParticipation().getSubmissions()).isNotEmpty();
        fetchedExercise.getTemplateParticipation().getSubmissions().forEach(submission -> {
            assertThat(submission.getResults()).isNotEmpty();
            assertThat(submission.getResults()).doesNotContainNull();
        });

        assertThat(fetchedExercise.getSolutionParticipation()).isNotNull();
        assertThat(fetchedExercise.getSolutionParticipation().getSubmissions()).isNotEmpty();
        fetchedExercise.getSolutionParticipation().getSubmissions().forEach(submission -> assertThat(submission.getResults()).isEmpty());
    }

    /**
     * This method is solely added to circumvent problem from open pull request
     * https://github.com/ls1intum/Artemis/pull/10997 :-(.
     * It can be deleted after resolving this issue
     */
    @Test
    void findAllByCourseId() {
        assertThat(programmingExerciseService.findAllByCourseId(9999L)).describedAs("course with id 9999 should not exist").isEmpty();
    }

}
