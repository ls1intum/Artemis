package de.tum.cit.aet.artemis.admin.service.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;

/**
 * A data export is the student's own copy of their work, so an export that quietly lacks their code is the one outcome
 * that must not happen: it looks complete and is wrong, and neither the student nor an administrator can tell.
 */
class DataExportExerciseCreationServiceTest {

    private static final String NOTE = "REPOSITORIES_MISSING.md";

    private ProgrammingExerciseExportService programmingExerciseExportService;

    private DataExportExerciseCreationService dataExportExerciseCreationService;

    private ProgrammingExercise programmingExercise;

    private User student;

    @TempDir
    Path exercisesDir;

    @BeforeEach
    void setUp() {
        programmingExerciseExportService = mock(ProgrammingExerciseExportService.class);
        dataExportExerciseCreationService = new DataExportExerciseCreationService(programmingExerciseExportService, mock(DataExportQuizExerciseCreationService.class),
                Optional.empty(), Optional.empty(), mock(ComplaintRepository.class), mock(ExerciseRepository.class), mock(ResultService.class),
                mock(AuthorizationCheckService.class), mock(ProgrammingFeedbackSynthesizerService.class));

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(1L);
        programmingExercise.setTitle("Sorting Algorithms");
        programmingExercise.setCourse(new Course());

        student = new User();
        student.setId(7L);
    }

    @Test
    void shouldRecordTheRepositoriesItCouldNotExport() throws Exception {
        // The export service reports a repository it could not export by adding to the list it is handed, and carries
        // on with the others. That list used to be a throwaway that nobody read.
        doAnswer(invocation -> {
            invocation.getArgument(4, List.class).add("Failed to export the student repository with participation: 42");
            return List.of();
        }).when(programmingExerciseExportService).exportStudentRepositories(any(), any(), any(), any(), anyList(), any(), any());

        dataExportExerciseCreationService.createProgrammingExerciseExport(programmingExercise, exercisesDir, student);

        Path note = exerciseDirectory().resolve(NOTE);
        assertThat(note).as("an export missing the student's code has to say so").exists();
        assertThat(Files.readString(note)).contains("participation: 42").contains("request a new export");
    }

    @Test
    void shouldNotLeaveANoteWhenEveryRepositoryWasExported() throws Exception {
        dataExportExerciseCreationService.createProgrammingExerciseExport(programmingExercise, exercisesDir, student);

        assertThat(exerciseDirectory().resolve(NOTE)).as("a complete export must not worry the student").doesNotExist();
    }

    /**
     * @return the directory the export created for the exercise, whose name carries its sanitized title
     */
    private Path exerciseDirectory() throws Exception {
        try (var entries = Files.list(exercisesDir)) {
            return entries.findFirst().orElseThrow(() -> new AssertionError("the export did not create a directory for the exercise"));
        }
    }
}
