package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExamApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationCapabilityService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

@ExtendWith(MockitoExtension.class)
class GenerationVariantDraftServiceTest {

    @Mock
    private ProgrammingExerciseTestRepository exercises;

    @Mock
    private ProgrammingExerciseBuildConfigRepository buildConfigs;

    @Mock
    private ProgrammingExerciseImportService imports;

    @Mock
    private ExamApi exams;

    @Mock
    private GenerationCapabilityService capabilities;

    private GenerationVariantDraftService service;

    private ProgrammingExercise source;

    private final VariantGenerationRequestDTO request = new VariantGenerationRequestDTO(DifficultyLevel.HARD, "Library", null, null, null);

    @BeforeEach
    void setUp() {
        service = new GenerationVariantDraftService(exercises, buildConfigs, imports, Optional.of(exams), capabilities);
        source = new ProgrammingExercise();
        source.setId(1L);
        source.setTitle("Source");
        source.setDifficulty(DifficultyLevel.EASY);
        source.setMaxPoints(10.0);
        var course = new Course();
        course.setId(10L);
        source.setCourse(course);
    }

    private void sourceGraph() {
        when(exercises.findForAuthoringImportById(1L)).thenReturn(Optional.of(source));
    }

    @Test
    void courseDraftIsUnreleasedAndReservedInsideTheMetadataTransactionWithoutCopying() {
        source.setReleaseDate(ZonedDateTime.now().minusDays(2));
        when(exercises.findWithAllParticipationsById(1L)).thenReturn(Optional.of(source));
        sourceGraph();
        var config = new ProgrammingExerciseBuildConfig();
        when(buildConfigs.getProgrammingExerciseBuildConfigElseThrow(1L)).thenReturn(config);
        AtomicBoolean inTransaction = new AtomicBoolean();
        when(exercises.prepareAuthoringDraft(any())).thenAnswer(invocation -> {
            inTransaction.set(true);
            try {
                return invocation.<Supplier<Object>>getArgument(0).get();
            }
            finally {
                inTransaction.set(false);
            }
        });
        when(imports.prepareImport(eq(source), eq(config), any(), isNull())).thenAnswer(invocation -> {
            assertThat(inTransaction).isTrue();
            ProgrammingExercise draft = invocation.getArgument(2);
            draft.setId(2L);
            return draft;
        });

        var draft = service.prepare(1L, request, destination -> {
            assertThat(inTransaction).isTrue();
            assertThat(destination.getId()).isEqualTo(2L);
            return destination;
        });

        assertThat(inTransaction).isFalse();
        assertThat(draft.getCourseViaExerciseGroupOrCourseMember()).isSameAs(source.getCourseViaExerciseGroupOrCourseMember());
        assertThat(draft.getReleaseDate()).isAfter(ZonedDateTime.now());
        assertThat(draft.getDifficulty()).isEqualTo(DifficultyLevel.HARD);
        assertThat(source.getDifficulty()).isEqualTo(DifficultyLevel.EASY);
        assertThat(source.getReleaseDate()).isBefore(ZonedDateTime.now());
        assertThat(draft.getMaxPoints()).isEqualTo(source.getMaxPoints());
        verify(capabilities).requireSupportedConfiguration(source);
        verifyNoInteractions(exams);
    }

    @Test
    void examEligibilityIsRecheckedAfterAcquiringTheAssignmentRowLock() {
        var exam = new Exam();
        exam.setId(20L);
        var group = new ExerciseGroup();
        group.setExam(exam);
        source.setCourse(null);
        source.setExerciseGroup(group);
        when(exercises.findWithAllParticipationsById(1L)).thenReturn(Optional.of(source));
        AtomicBoolean locked = new AtomicBoolean();
        when(exams.withExercisePreparationLock(eq(20L), any())).thenAnswer(invocation -> {
            locked.set(true);
            return invocation.<Supplier<Object>>getArgument(1).get();
        });
        doThrow(new IllegalStateException("assigned meanwhile")).when(capabilities).requireMutable(source);

        assertThatThrownBy(() -> service.prepare(1L, request, Function.identity())).hasMessage("assigned meanwhile");

        assertThat(locked).isTrue();
        verifyNoInteractions(imports, buildConfigs);
    }

    @Test
    void remoteCompletionUsesTheExistingImportPathOnlyAfterDestinationExists() {
        sourceGraph();
        var destination = new ProgrammingExercise();
        destination.setId(2L);
        when(exercises.findForCreationById(2L)).thenReturn(Optional.of(destination));

        service.complete(1L, 2L);

        verify(imports).completeImport(source, destination, true, false);
        verifyNoInteractions(buildConfigs, exams, capabilities);
    }
}
