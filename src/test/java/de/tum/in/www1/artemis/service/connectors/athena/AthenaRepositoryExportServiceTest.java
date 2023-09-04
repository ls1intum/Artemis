package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
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
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

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
    void shouldExportRepository() throws IOException, GitAPIException {
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

        Path zipPath = Paths.get("/export/dir/zipfile.zip");

        Repository mockRepository = mock(Repository.class);
        doReturn(true).when(mockRepository).isValidFile(any());
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), any(), any(), anyBoolean(), anyString());
        doReturn(testRepo.localRepoFile.toPath()).when(mockRepository).getLocalPath();
        doNothing().when(gitService).resetToOriginHead(any());
        doReturn(zipPath).when(gitService).zipRepositoryWithParticipation(any(), anyString(), anyBoolean());

        File resultStudentRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), null);
        File resultSolutionRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), RepositoryType.SOLUTION);

        assertThat(resultStudentRepo.toPath()).isEqualTo(zipPath); // The student repository ZIP is returned
        assertThat(resultSolutionRepo).exists(); // The solution repository ZIP can actually be created in the test
    }

    @Test
    void shouldThrowAccessForbiddenWhenFeedbackSuggestionsNotEnabled() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionsEnabled(false);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), null, null));
    }
}
