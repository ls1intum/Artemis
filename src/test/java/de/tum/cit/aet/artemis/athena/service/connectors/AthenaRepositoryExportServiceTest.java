package de.tum.cit.aet.artemis.athena.service.connectors;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.athena.service.AthenaModuleService;
import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
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
    private AthenaModuleService athenaModuleService;

    private final LocalRepository testRepo = new LocalRepository(defaultBranch);

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        testRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");

        // add test file to the repository folder
        Path filePath = Path.of(testRepo.workingCopyGitRepoFile + "/Test.java");
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, "Test", Charset.defaultCharset());
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

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUri("http://localhost:4912/git/SHORTNAME/shortname-student1.git");
        participation.setProgrammingExercise(programmingExerciseWithId);
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        var programmingSubmissionWithId = programmingExerciseUtilService.addProgrammingSubmission(programmingExerciseWithId, submission, TEST_PREFIX + "student1");

        programmingExerciseUtilService.createGitRepository();

        Map<String, String> resultStudentRepo = athenaRepositoryExportService.getStudentRepositoryFilesContent(programmingExerciseWithId.getId(),
                programmingSubmissionWithId.getId());
        Map<String, String> resultSolutionRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.SOLUTION);

        assertThat(resultStudentRepo).isNotNull(); // The student repository files are returned
        assertThat(resultSolutionRepo).isNotNull(); // The solution repository files are returned
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void shouldExportAllValidInstructorRepositoryTypes() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository.findAllByCourseId(course.getId()).getFirst();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        programmingExerciseUtilService.createGitRepository();

        Map<String, String> templateRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.TEMPLATE);
        Map<String, String> solutionRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.SOLUTION);
        Map<String, String> testsRepo = athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.TESTS);

        assertThat(templateRepo).isNotNull();
        assertThat(solutionRepo).isNotNull();
        assertThat(testsRepo).isNotNull();
    }

    @Test
    void shouldThrowServiceUnavailableWhenFeedbackSuggestionsNotEnabled() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(null);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        assertThatExceptionOfType(ServiceUnavailableException.class).as("Should throw ServiceUnavailableException when feedback suggestions are not enabled")
                .isThrownBy(() -> athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), RepositoryType.TEMPLATE))
                .withMessageContaining("Feedback suggestions are not enabled");
    }

    @Test
    void shouldThrowBadRequestAlertExceptionForInvalidRepositoryType() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        var invalidRepositoryTypes = Set.of(RepositoryType.USER, RepositoryType.AUXILIARY);
        for (var invalidRepositoryType : invalidRepositoryTypes) {
            assertThatExceptionOfType(BadRequestAlertException.class).as("Should throw BadRequestAlertException for invalid repository type: " + invalidRepositoryType)
                    .isThrownBy(() -> athenaRepositoryExportService.getInstructorRepositoryFilesContent(programmingExerciseWithId.getId(), invalidRepositoryType))
                    .withMessageContaining("Invalid instructor repository type")
                    .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("invalid.instructor.repository.type"));
        }
    }

    @Test
    void shouldThrowBadRequestAlertExceptionWhenFeedbackSuggestionModuleIsNull() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(null);

        assertThatExceptionOfType(BadRequestAlertException.class).as("Should throw BadRequestAlertException when feedback suggestion module is null")
                .isThrownBy(() -> athenaModuleService.getAthenaModuleUrl(programmingExercise))
                .withMessageContaining("Exercise does not have a feedback suggestion module configured");
    }
}
