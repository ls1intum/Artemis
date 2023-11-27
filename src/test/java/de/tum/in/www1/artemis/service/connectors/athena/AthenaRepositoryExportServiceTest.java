package de.tum.in.www1.artemis.service.connectors.athena;

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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.rest.errors.ServiceUnavailableException;

class AthenaRepositoryExportServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
        programmingExercise.setFeedbackSuggestionsEnabled(true);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUrl("git://test");
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
        programmingExercise.setFeedbackSuggestionsEnabled(false);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        assertThatExceptionOfType(ServiceUnavailableException.class)
                .isThrownBy(() -> athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), null, null));
    }
}
