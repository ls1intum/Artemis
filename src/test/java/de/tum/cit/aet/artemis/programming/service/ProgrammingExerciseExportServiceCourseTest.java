package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.localvc.service.GitRepositoryExportService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for the course lookup of {@link ProgrammingExerciseExportService}: the name of an export archive is derived from the course of the exercise, so an exercise whose
 * course cannot be resolved must fail with a clear message instead of a {@link NullPointerException}.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseExportServiceCourseTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Mock
    private StudentParticipationTestRepository studentParticipationRepository;

    @Mock
    private FileService fileService;

    @Mock
    private GitService gitService;

    @Mock
    private GitRepositoryExportService gitRepositoryExportService;

    @Mock
    private RepositoryExportGitService repositoryExportGitService;

    @Mock
    private ZipFileService zipFileService;

    @Mock
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Mock
    private BuildPlanRepository buildPlanRepository;

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @TempDir
    Path outputDir;

    private ProgrammingExerciseExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new ProgrammingExerciseExportService(programmingExerciseRepository, programmingExerciseTaskService, studentParticipationRepository, fileService, gitService,
                gitRepositoryExportService, repositoryExportGitService, zipFileService, JsonMapper.builder().build(), auxiliaryRepositoryRepository, buildPlanRepository,
                programmingExerciseBuildConfigRepository, mock(TeamAssignmentConfigRepository.class));
    }

    @Test
    void exportProgrammingExerciseRepositories_forAnExerciseWithoutCourse_throwsWithTheExerciseId() {
        var exercise = new ProgrammingExercise();
        exercise.setId(4711L);
        exercise.setTitle("Orphan");
        // the repository lookups find nothing (mock default), so only the archive name remains to be derived

        assertThatThrownBy(() -> exportService.exportProgrammingExerciseRepositories(exercise, false, true, outputDir, new ArrayList<>(), new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("4711");
    }
}
