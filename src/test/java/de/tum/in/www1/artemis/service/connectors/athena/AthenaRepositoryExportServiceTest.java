package de.tum.in.www1.artemis.service.connectors.athena;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

class AthenaRepositoryExportServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "athenarepositoryexport";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @MockBean
    private ProgrammingExerciseExportService programmingExerciseExportService;

    @Mock
    private FileService fileService;

    @Autowired
    private AthenaRepositoryExportService athenaRepositoryExportService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void shouldExportRepository() throws IOException {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository.findByCourseIdWithLatestResultForTemplateSolutionParticipations(course.getId()).stream().iterator().next();
        programmingExercise.setFeedbackSuggestionsEnabled(true);
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        ProgrammingSubmission submission = new ProgrammingSubmission();
        var programmingSubmissionWithId = programmingExerciseUtilService.addProgrammingSubmission(programmingExerciseWithId, submission, TEST_PREFIX + "student1");

        Path exportDir = Paths.get("/export/dir");
        Path zipPath1 = Paths.get("/export/dir/zipfile1.zip");
        File zipFile1 = zipPath1.toFile();
        Path zipPath2 = Paths.get("/export/dir/zipfile2.zip");
        File zipFile2 = zipPath2.toFile();

        when(fileService.getTemporaryUniquePath(any(), anyInt())).thenReturn(exportDir);
        when(programmingExerciseExportService.createZipForRepositoryWithParticipation(any(), any(), any(), any(), any())).thenReturn(zipPath1);
        when(programmingExerciseExportService.exportInstructorRepositoryForExercise(anyLong(), any(), any(), any())).thenReturn(Optional.of(zipFile2));

        File resultStudentRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), null);
        File resultSolutionRepo = athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), programmingSubmissionWithId.getId(), RepositoryType.SOLUTION);

        assertEquals(zipFile1, resultStudentRepo);
        assertEquals(zipFile2, resultSolutionRepo);
    }

    @Test
    void shouldThrowAccessForbiddenWhenFeedbackSuggestionsNotEnabled() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setFeedbackSuggestionsEnabled(false);
        var programmingExerciseWithId = programmingExerciseRepository.save(programmingExercise);

        assertThrows(AccessForbiddenException.class, () -> athenaRepositoryExportService.exportRepository(programmingExerciseWithId.getId(), null, null));
    }
}
