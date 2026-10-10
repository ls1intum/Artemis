import { Injector, Signal, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGenerationCapabilities } from 'app/openapi/model/exercise-generation-capabilities';
import { GenerationCapabilityRequestsService } from './generation-capability-requests.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { supportsHyperionExerciseGeneration } from './hyperion-generation-support';

/** Cheap presentation prefilter; lifecycle and repository policy remain authoritative on the server. */
export function hasSupportedProgrammingConfiguration(exercise: Exercise | undefined): boolean {
    if (exercise?.type !== ExerciseType.PROGRAMMING) return false;
    const programming = exercise as ProgrammingExercise;
    return (
        supportsHyperionExerciseGeneration(programming.programmingLanguage, programming.projectType) &&
        !programming.staticCodeAnalysisEnabled &&
        !programming.buildConfig?.sequentialTestRuns &&
        !programming.auxiliaryRepositories?.length
    );
}

/** Cancels stale exercise requests and fails closed. Quiz surfaces never request programming capabilities. */
export function injectGenerationCapabilities(exercise: Signal<Exercise | undefined>, authorized: Signal<boolean>) {
    const injector = inject(Injector);
    const profiles = inject(ProfileService);
    return rxResource({
        params: () => {
            const current = exercise();
            return authorized() && profiles.isModuleFeatureActive(MODULE_FEATURE_HYPERION_EXERCISE_GENERATION) && hasSupportedProgrammingConfiguration(current) && current?.id
                ? { exerciseId: current.id, exercise: current }
                : undefined;
        },
        stream: ({ params }) =>
            injector
                .get(GenerationCapabilityRequestsService)
                .describe(params.exerciseId)
                .pipe(catchError(() => of(undefined))),
    });
}

/** A missing capability response must not accidentally enable an action. Admission always rechecks. */
export function generationCapabilityBlocker(capabilities: ExerciseGenerationCapabilities | undefined): string | undefined {
    const prefix = 'artemisApp.hyperion.generation.blocker.';
    if (!capabilities) return prefix + 'capabilitiesUnavailable';
    if (capabilities.canAdapt) return undefined;
    switch (capabilities.restriction) {
        case 'exerciseAlreadyReleased':
            return prefix + 'released';
        case 'exerciseHasParticipations':
            return prefix + 'studentParticipations';
        default:
            return prefix + 'preparationOnly';
    }
}
