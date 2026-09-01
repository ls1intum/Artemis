import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiButtonComponent, TumUiMessageComponent, TumUiPanelComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionFileChangeListComponent } from 'app/hyperion/exercise-generation/run/hyperion-file-change-list.component';
import { ExerciseGenerationFileChange } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';

/**
 * Everything a run has produced, before any of it is part of the exercise.
 *
 * The retained problem statement is fetched lazily: it only exists once a run has finished without saving, and
 * asking for it while the run is still going would be a request that can only 404.
 */
@Component({
    selector: 'jhi-hyperion-artifacts',
    templateUrl: './hyperion-artifacts.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, HyperionFileChangeListComponent, TumUiButtonComponent, TumUiMessageComponent, TumUiPanelComponent],
})
export class HyperionArtifactsComponent {
    private readonly api = inject(HyperionExerciseGenerationApi);
    private readonly destroyRef = inject(DestroyRef);

    readonly exerciseId = input<number | undefined>();
    /** The design document the agent wrote before touching code, as reported by the run status. */
    readonly specDocument = input<string | undefined>();
    /** The exercise's own problem statement, set only once a run actually saved one. */
    readonly savedProblemStatement = input<string | undefined>();
    readonly files = input<readonly ExerciseGenerationFileChange[]>([]);
    readonly running = input(false);
    /** Whether the run has ended, however it ended. */
    readonly terminal = input(false);

    protected readonly specCollapsed = signal(false);
    protected readonly statementCollapsed = signal(false);
    protected readonly filesCollapsed = signal(false);

    private readonly retainedProblemStatement = signal<string | undefined>(undefined);
    protected readonly retainedLoadFailed = signal(false);
    protected readonly retainedLoading = signal(false);
    private retainedRequestedFor?: number;

    /** The live exercise wins: once the draft is saved, what the instructor sees must be what is actually stored. */
    protected readonly problemStatement = computed(() => this.savedProblemStatement() ?? this.retainedProblemStatement());

    protected readonly hasSpecDocument = computed(() => (this.specDocument()?.trim().length ?? 0) > 0);
    protected readonly hasProblemStatement = computed(() => (this.problemStatement()?.trim().length ?? 0) > 0);

    constructor() {
        // A finished run is something to read through, not to watch, so it opens folded up.
        effect(() => {
            const terminal = this.terminal();
            untracked(() => {
                this.specCollapsed.set(terminal);
                this.statementCollapsed.set(terminal);
                this.filesCollapsed.set(terminal);
            });
        });

        effect(() => {
            const exerciseId = this.exerciseId();
            const wanted = this.terminal() && !this.statementCollapsed() && this.savedProblemStatement() === undefined && exerciseId !== undefined;
            if (wanted) {
                untracked(() => this.loadRetainedArtifacts(exerciseId));
            }
        });
    }

    /** Retries a failed fetch; the guard on `retainedRequestedFor` is what stops the effect from retrying by itself. */
    protected retryRetainedArtifacts(): void {
        this.retainedRequestedFor = undefined;
        const exerciseId = this.exerciseId();
        if (exerciseId !== undefined) {
            this.loadRetainedArtifacts(exerciseId);
        }
    }

    private loadRetainedArtifacts(exerciseId: number): void {
        if (this.retainedRequestedFor === exerciseId) {
            return;
        }
        this.retainedRequestedFor = exerciseId;
        this.retainedLoading.set(true);
        this.retainedLoadFailed.set(false);
        this.api
            .getRetainedGenerationArtifacts(exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (artifacts) => {
                    this.retainedLoading.set(false);
                    this.retainedProblemStatement.set(artifacts.problemStatement);
                },
                // Never swallowed: an instructor who cannot see the retained draft must be told why. A 404 is the
                // exception — it is the server saying this run kept nothing, which the "not written yet" copy covers.
                error: (error: unknown) => {
                    this.retainedLoading.set(false);
                    this.retainedLoadFailed.set(!(error instanceof HttpErrorResponse && error.status === 404));
                },
            });
    }
}
