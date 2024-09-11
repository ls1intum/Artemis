package de.tum.cit.aet.artemis.service.connectors.athena;

import static de.tum.cit.aet.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.user.UserUtilService;
import de.tum.cit.aet.artemis.util.LocalRepository;

class AthenaRepositoryExportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "athenarepositoryexport";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private AthenaRepositoryExportService athenaRepositoryExportService;

    private final LocalRepository testRepo = new LocalRepository(defaultBranch);

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        testRepo.configureRepos("testLocalRepo", "testOriginRepo");

        // add test file to the repository folder
        Path filePath = Path.of(testRepo.localRepoFile + "/Test.java");
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, "Test", Charset.defaultCharset());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void shouldExportRepository() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository.findByCourseIdWithLatestResultForTemplateSolutionParticipations(course.getId()).stream().iterator().next();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUri("git://test");
        participation.setProgrammingExercise(programmingExerciseWithId);
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        var programmingSubmissionWithId = programmingExerciseUtilService.addProgrammingSubmission(programmingExerciseWithId, submission, TEST_PREFIX + "student1");

        programmingExerciseUtilService.createGitRepository();

        File resultStudentRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), null);
        File resultSolutionRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), RepositoryType.SOLUTION);

        assertThat(resultStudentRepo.toPath()).isEqualTo(Paths.get("repo.zip")); // The student repository ZIP is returned
        assertThat(resultSolutionRepo).exists(); // The solution repository ZIP can actually be created in the test
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
