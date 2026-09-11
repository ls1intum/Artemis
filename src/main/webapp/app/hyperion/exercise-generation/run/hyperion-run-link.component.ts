import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, exhaustMap, merge, of, startWith, switchMap, timer } from 'rxjs';
import { TumUiButtonDirective } from '@tumaet/ui-angular';
import { MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionExerciseGenerationService } from '../hyperion-exercise-generation.service';
import { supportsHyperionExerciseGeneration } from '../hyperion-generation-support';

/** Re-entry from exercise details, visible only while the server still has a run or result. */
@Component({
    selector: 'jhi-hyperion-run-link',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, TumUiButtonDirective, TranslateDirective],
    template: `
        @if (status()?.jobId) {
            <a tumUiButton severity="secondary" variant="text" size="small" [routerLink]="['generation']" data-testid="hyperion-exercise-open-generation">
                <span [jhiTranslate]="labelKey()"></span>
            </a>
        }
    `,
})
export class HyperionRunLinkComponent {
    readonly exercise = input.required<ProgrammingExercise>();
    private readonly service = inject(HyperionExerciseGenerationService);
    private readonly profile = inject(ProfileService);
    private readonly exerciseId = computed(() => {
        const exercise = this.exercise();
        return exercise.isAtLeastEditor &&
            this.profile.isModuleFeatureActive(MODULE_FEATURE_HYPERION_EXERCISE_GENERATION) &&
            supportsHyperionExerciseGeneration(exercise.programmingLanguage, exercise.projectType)
            ? exercise.id
            : undefined;
    });
    protected readonly status = toSignal(
        toObservable(this.exerciseId).pipe(
            switchMap((id) =>
                id === undefined
                    ? of(undefined)
                    : merge(timer(30_000, 30_000), this.service.subscribeToExerciseState(id)).pipe(
                          startWith(undefined),
                          exhaustMap(() => this.service.getStatus(id).pipe(catchError(() => of(undefined)))),
                          startWith(undefined),
                      ),
            ),
        ),
    );
    protected readonly labelKey = computed(() => `artemisApp.hyperion.generation.actions.${this.status()?.running ? 'viewRun' : 'viewResults'}`);
}
