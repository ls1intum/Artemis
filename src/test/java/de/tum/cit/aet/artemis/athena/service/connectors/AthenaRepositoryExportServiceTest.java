package de.tum.cit.aet.artemis.athena.service.connectors;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class AthenaRepositoryExportServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "athenarepositoryexport";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private AthenaRepositoryExportService athenaRepositoryExportService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void shouldExportRepository() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository.findAllByCourseId(course.getId()).getFirst();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);
        // Reload with eager references so repository URIs are available
        programmingExerciseWithId = programmingExerciseRepository.findWithEagerTemplateAndSolutionParticipationsById(programmingExerciseWithId.getId()).orElseThrow();

        // Create actual LocalVC repositories for template, solution and a student participation
        var templateRepoUri = programmingExerciseWithId.getRepositoryURI(RepositoryType.TEMPLATE);
        var solutionRepoUri = programmingExerciseWithId.getRepositoryURI(RepositoryType.SOLUTION);

        // Ensure template repository exists with an initial commit
        var templateOriginFolder = templateRepoUri.getLocalRepositoryPath(localVCBasePath);
        var templateLocal = new LocalRepository(defaultBranch);
        templateLocal.configureRepos(localVCBasePath, "templateLocalRepo", templateOriginFolder);

        // Ensure solution repository exists with an initial commit
        var solutionOriginFolder = solutionRepoUri.getLocalRepositoryPath(localVCBasePath);
        var solutionLocal = new LocalRepository(defaultBranch);
        solutionLocal.configureRepos(localVCBasePath, "solutionLocalRepo", solutionOriginFolder);

        // Create a student participation with a submission and ensure repository exists
        var result = participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExerciseWithId, TEST_PREFIX + "student1");
        var studentParticipation = (ProgrammingExerciseStudentParticipation) result.getSubmission().getParticipation();
        var studentRepoUri = new de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri(studentParticipation.getRepositoryUri());
        var studentOriginFolder = studentRepoUri.getLocalRepositoryPath(localVCBasePath);
        var studentLocal = new LocalRepository(defaultBranch);
        studentLocal.configureRepos(localVCBasePath, "studentLocalRepo", studentOriginFolder);

        InputStreamResource resultStudentRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), result.getSubmission().getId(), null);
        InputStreamResource resultSolutionRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), null, RepositoryType.SOLUTION);

        assertThat(resultStudentRepo.getFilename()).isNotNull();
        assertThat(resultStudentRepo.getFilename()).endsWith(".zip"); // The student repository ZIP is returned
        assertThat(resultSolutionRepo.exists()).isTrue(); // The solution repository ZIP can actually be created in the test
    }

    @Test
    void shouldThrowServiceUnavailableWhenFeedbackSuggestionsNotEnabled() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(null);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        assertThatExceptionOfType(ServiceUnavailableException.class)
                .isThrownBy(() -> athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), null, null));
    }
}
