package de.tum.cit.aet.artemis.athena.service.connectors;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
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
        programmingExercise.setAthenaConfig(ExerciseAthenaConfig.of(ATHENA_MODULE_PROGRAMMING_TEST, null));
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUri("git://test");
        participation.setProgrammingExercise(programmingExerciseWithId);
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        var programmingSubmissionWithId = programmingExerciseUtilService.addProgrammingSubmission(programmingExerciseWithId, submission, TEST_PREFIX + "student1");

        programmingExerciseUtilService.createGitRepository();

        Path resultStudentRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), null);
        Path resultSolutionRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), RepositoryType.SOLUTION);

        assertThat(resultStudentRepo).isEqualTo(Path.of("repo.zip")); // The student repository ZIP is returned
        assertThat(resultSolutionRepo).exists(); // The solution repository ZIP can actually be created in the test
    }

    @Test
    void shouldThrowServiceUnavailableWhenFeedbackSuggestionsNotEnabled() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setAthenaConfig(null);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        assertThatExceptionOfType(ServiceUnavailableException.class)
                .isThrownBy(() -> athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), null, null));
    }
}
