package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;

/**
 * The team assignment and plagiarism detection settings are not part of an exercise, so importing a programming exercise
 * through a competency only copies them if the source exercise carries them when the import starts.
 */
class LearningObjectImportServiceTest {

    private final ProgrammingExerciseTestRepository programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);

    private final ProgrammingExerciseImportService programmingExerciseImportService = mock(ProgrammingExerciseImportService.class);

    private final ExerciseConfigurationService exerciseConfigurationService = mock(ExerciseConfigurationService.class);

    private final LearningObjectImportService service = new LearningObjectImportService(mock(ExerciseRepository.class), programmingExerciseRepository,
            programmingExerciseImportService, Optional.empty(), Optional.empty(), Optional.empty(), mock(QuizExerciseRepository.class), mock(QuizExerciseImportService.class),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), mock(CourseCompetencyRepository.class), mock(ProgrammingExerciseTaskRepository.class),
            mock(GradingCriterionRepository.class), mock(CompetencyExerciseLinkRepository.class), mock(CompetencyLectureUnitLinkRepository.class), exerciseConfigurationService);

    @Test
    void importingATeamProgrammingExerciseCopiesItsStoredTeamAndPlagiarismSettings() {
        var team = new TeamAssignmentConfig();
        team.setMinTeamSize(2);
        team.setMaxTeamSize(5);
        var plagiarism = PlagiarismDetectionConfig.createDefault();
        plagiarism.setSimilarityThreshold(42);

        ProgrammingExercise imported = importWithStoredSettings(ExerciseMode.TEAM, team, plagiarism);

        assertThat(imported.getStoredTeamAssignmentConfig()).isNotNull();
        assertThat(imported.getStoredTeamAssignmentConfig().getMinTeamSize()).isEqualTo(2);
        assertThat(imported.getStoredTeamAssignmentConfig().getMaxTeamSize()).isEqualTo(5);
        assertThat(imported.getPlagiarismDetectionConfig()).isNotNull();
        assertThat(imported.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(42);
    }

    @Test
    void importingAnIndividualProgrammingExerciseStillReadsBothSettingsBeforeTheImport() {
        var team = new TeamAssignmentConfig();
        team.setMinTeamSize(3);
        team.setMaxTeamSize(4);
        var plagiarism = PlagiarismDetectionConfig.createDefault();
        plagiarism.setSimilarityThreshold(77);

        ProgrammingExercise imported = importWithStoredSettings(ExerciseMode.INDIVIDUAL, team, plagiarism);

        // Both reads happen whatever the mode. What the real import then does with the team sizes of an individual exercise (it
        // drops them, because the team getter reports none) is covered by the integration test of the competency import.
        verify(exerciseConfigurationService).attachTeamAssignmentConfig(imported);
        verify(exerciseConfigurationService).attachPlagiarismDetectionConfig(imported);
        assertThat(imported.getPlagiarismDetectionConfig().getSimilarityThreshold()).isEqualTo(77);
    }

    private ProgrammingExercise importWithStoredSettings(ExerciseMode mode, TeamAssignmentConfig team, PlagiarismDetectionConfig plagiarism) {
        Course course = new Course();
        course.setId(1L);
        course.setShortName("crs");

        ProgrammingExercise source = new ProgrammingExercise();
        source.setId(10L);
        source.setTitle("Source");
        source.setShortName("src");

        ProgrammingExercise loadedForImport = new ProgrammingExercise();
        loadedForImport.setId(10L);
        loadedForImport.setTitle("Source");
        loadedForImport.setShortName("src");
        loadedForImport.setMode(mode);

        when(programmingExerciseRepository.findByIdForImportElseThrow(10L)).thenReturn(source);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationCategoriesCompetenciesAndVariantGroupElseThrow(10L)).thenReturn(loadedForImport);
        // what the real service does: read the stored rows of the exercise onto its slots
        doAnswer(invocation -> {
            invocation.<Exercise>getArgument(0).setTeamAssignmentConfig(team);
            return null;
        }).when(exerciseConfigurationService).attachTeamAssignmentConfig(any());
        doAnswer(invocation -> {
            invocation.<Exercise>getArgument(0).setPlagiarismDetectionConfig(plagiarism);
            return null;
        }).when(exerciseConfigurationService).attachPlagiarismDetectionConfig(any());
        when(programmingExerciseImportService.importProgrammingExercise(any(), any(), eq(false), eq(false))).thenAnswer(invocation -> invocation.getArgument(1));

        ReflectionTestUtils.invokeMethod(service, "importOrLoadProgrammingExercise", source, course);

        var imported = ArgumentCaptor.forClass(ProgrammingExercise.class);
        verify(programmingExerciseImportService).importProgrammingExercise(any(), imported.capture(), eq(false), eq(false));
        return imported.getValue();
    }
}
