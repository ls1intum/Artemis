package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.AutomaticAfterDueDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.dto.UpdateBuildPlanConfigurationDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseBuildPlanService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationScheduleService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;

class ProgrammingExerciseBuildConfigResourceMutationGuardTest {

    @Test
    void activeGenerationRejectsBuildConfigurationBeforeReadsOrChanges() {
        var repository = mock(ProgrammingExerciseRepository.class);
        var configs = mock(ProgrammingExerciseBuildConfigRepository.class);
        var plans = mock(ProgrammingExerciseBuildPlanService.class);
        var triggers = mock(ProgrammingTriggerService.class);
        var users = mock(UserRepository.class);
        var versions = mock(ExerciseVersionService.class);
        var guard = mock(ProgrammingExerciseMutationGuardService.class);
        var conflict = new ConflictException("Generation is running", "programmingExercise", "generationRunning");
        when(guard.claimExternalMutation(71L)).thenThrow(conflict);
        var resource = new ProgrammingExerciseBuildConfigResource(repository, configs, plans, triggers, Optional.empty(), mock(ProgrammingExerciseCreationScheduleService.class),
                users, mock(ExerciseService.class), versions, guard);

        assertThatThrownBy(() -> resource.updateBuildConfig(71L, mock(UpdateBuildPlanConfigurationDTO.class))).isSameAs(conflict);

        verifyNoInteractions(repository, configs, plans, triggers, users, versions);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void leaseCoversReadAndSaveWithTheRequiredVersionMode(boolean coordinated) {
        var repository = mock(ProgrammingExerciseRepository.class);
        var configs = mock(ProgrammingExerciseBuildConfigRepository.class);
        var plans = mock(ProgrammingExerciseBuildPlanService.class);
        var dates = mock(AutomaticAfterDueDateService.class);
        var users = mock(UserRepository.class);
        var versions = mock(ExerciseVersionService.class);
        var guard = mock(ProgrammingExerciseMutationGuardService.class);
        var held = new AtomicBoolean();
        var exercise = new ProgrammingExercise();
        exercise.setId(71L);
        exercise.setProblemStatement("Current statement");
        var config = new ProgrammingExerciseBuildConfig();
        var user = new User();
        var dto = mock(UpdateBuildPlanConfigurationDTO.class);
        var date = ZonedDateTime.parse("2026-11-01T12:00:00Z");
        when(guard.claimExternalMutation(71L)).thenAnswer(_ -> {
            held.set(coordinated);
            return new ProgrammingExerciseMutationGuardService.MutationLease(() -> held.set(false), coordinated);
        });
        when(repository.findByIdElseThrow(71L)).thenAnswer(_ -> {
            assertThat(held.get()).isEqualTo(coordinated);
            return exercise;
        });
        when(configs.getProgrammingExerciseBuildConfigElseThrow(71L)).thenReturn(config);
        when(users.getUserWithAuthorities()).thenReturn(user);
        when(plans.updateBuildPlanConfiguration(exercise, config, dto)).thenReturn(config);
        var offset = Duration.ofHours(1);
        when(dates.getOriginalBuildAndTestOffset(exercise)).thenReturn(offset);
        when(dates.computeBuildAndTestDate(exercise, config, offset)).thenReturn(date);
        when(repository.save(exercise)).thenAnswer(_ -> {
            assertThat(held.get()).isEqualTo(coordinated);
            assertThat(exercise.getProblemStatement()).isEqualTo("Current statement");
            assertThat(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()).isEqualTo(date);
            return exercise;
        });
        var selectedVersionService = doAnswer(_ -> {
            assertThat(held.get()).isEqualTo(coordinated);
            return null;
        }).when(versions);
        if (coordinated) {
            selectedVersionService.createExerciseVersionSynchronously(exercise, user);
        }
        else {
            selectedVersionService.createExerciseVersion(exercise, user);
        }
        var resource = new ProgrammingExerciseBuildConfigResource(repository, configs, plans, mock(ProgrammingTriggerService.class), Optional.of(dates),
                mock(ProgrammingExerciseCreationScheduleService.class), users, mock(ExerciseService.class), versions, guard);

        assertThat(resource.updateBuildConfig(71L, dto).getStatusCode().value()).isEqualTo(200);

        verify(repository).save(exercise);
        if (coordinated) {
            verify(versions).createExerciseVersionSynchronously(exercise, user);
        }
        else {
            verify(versions).createExerciseVersion(exercise, user);
        }
        assertThat(held).isFalse();
    }
}
