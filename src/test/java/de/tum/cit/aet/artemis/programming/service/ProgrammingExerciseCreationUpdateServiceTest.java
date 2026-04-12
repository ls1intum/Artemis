package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseCreationUpdateServiceTest {

    @Mock
    private ModuleFeatureService moduleFeatureService;

    @Mock
    private UserTestRepository userRepository;

    private ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    @BeforeEach
    void setUp() {
        programmingExerciseCreationUpdateService = new ProgrammingExerciseCreationUpdateService(mock(ProgrammingExerciseRepositoryService.class),
                mock(ProgrammingExerciseBuildConfigRepository.class), mock(ProgrammingSubmissionService.class), userRepository, mock(ExerciseService.class),
                mock(ProgrammingExerciseRepository.class), mock(ChannelService.class), mock(ProgrammingExerciseTaskService.class), mock(ProgrammingExerciseBuildPlanService.class),
                mock(ProgrammingExerciseCreationScheduleService.class), mock(ProgrammingExerciseAtlasIrisService.class), moduleFeatureService,
                mock(TemplateProgrammingExerciseParticipationRepository.class), mock(SolutionProgrammingExerciseParticipationRepository.class),
                mock(AuxiliaryRepositoryRepository.class), Optional.<VersionControlService>empty(), mock(ParticipationRepository.class), mock(GitService.class),
                mock(CompetencyExerciseLinkService.class));
    }

    @Test
    void createProgrammingExercise_emptyRepositoriesAndHyperionDisabled_throwsBadRequest() {
        var exercise = createExercise(ProgrammingLanguage.JAVA);
        when(moduleFeatureService.isHyperionEnabled()).thenReturn(false);

        assertThatThrownBy(() -> programmingExerciseCreationUpdateService.createProgrammingExercise(exercise, true)).isInstanceOfSatisfying(BadRequestAlertException.class, ex -> {
            assertThat(ex.getMessage()).isEqualTo("Hyperion is disabled on this server");
            assertThat(ex.getErrorKey()).isEqualTo("hyperionDisabled");
        });
        verifyNoInteractions(userRepository);
    }

    @Test
    void createProgrammingExercise_emptyRepositoriesAndUnsupportedLanguage_throwsBadRequest() {
        var exercise = createExercise(ProgrammingLanguage.PYTHON);
        when(moduleFeatureService.isHyperionEnabled()).thenReturn(true);
        assertThatThrownBy(() -> programmingExerciseCreationUpdateService.createProgrammingExercise(exercise, true)).isInstanceOfSatisfying(BadRequestAlertException.class, ex -> {
            assertThat(ex.getMessage()).isEqualTo("AI generation is only supported for Java");
            assertThat(ex.getErrorKey()).isEqualTo("aiGenerationUnsupportedLanguage");
        });
        verifyNoInteractions(userRepository);
    }

    @Test
    void createProgrammingExercise_nullExercise_throwsBadRequest() {
        assertThatThrownBy(() -> programmingExerciseCreationUpdateService.createProgrammingExercise(null, false)).isInstanceOfSatisfying(BadRequestAlertException.class, ex -> {
            assertThat(ex.getMessage()).isEqualTo("ProgrammingExercise must not be null");
            assertThat(ex.getErrorKey()).isEqualTo("programmingExerciseNull");
        });
        verifyNoInteractions(userRepository);
    }

    @Test
    void createProgrammingExercise_missingBuildConfig_throwsBadRequest() {
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);

        assertThatThrownBy(() -> programmingExerciseCreationUpdateService.createProgrammingExercise(exercise, false)).isInstanceOfSatisfying(BadRequestAlertException.class, ex -> {
            assertThat(ex.getMessage()).isEqualTo("ProgrammingExercise build config must not be null");
            assertThat(ex.getErrorKey()).isEqualTo("buildConfigMissing");
        });
        verifyNoInteractions(userRepository);
    }

    private static ProgrammingExercise createExercise(ProgrammingLanguage language) {
        var exercise = new ProgrammingExercise();
        exercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        exercise.setProgrammingLanguage(language);
        return exercise;
    }
}
