import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProgrammingExerciseWebsocketService } from 'app/programming/manage/services/programming-exercise-websocket.service';
import { tap } from 'rxjs/operators';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Two status indicators for the test case table:
 * - Are there unsaved changes?
 * - Have test cases been changed but the student submissions were not triggered?
 */
@Component({
    selector: 'jhi-programming-exercise-grading-dirty-warning',
    template: `
        @if (hasUpdatedGradingConfig()) {
            <fa-icon
                [icon]="faExclamationTriangle"
                class="text-warning"
                size="2x"
                ngbTooltip="{{ 'artemisApp.programmingExercise.configureGrading.updatedGradingConfigTooltip' | artemisTranslate }}"
            />
        }
    `,
    imports: [FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class ProgrammingExerciseGradingDirtyWarningComponent {
    private programmingExerciseWebsocketService = inject(ProgrammingExerciseWebsocketService);
    private destroyRef = inject(DestroyRef);

    readonly programmingExerciseId = input.required<number>();
    readonly hasUpdatedGradingConfigInitialValue = input.required<boolean>();

    protected readonly hasUpdatedGradingConfig = signal<boolean | undefined>(undefined);

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    constructor() {
        // When the programming exercise changes, reset the updated-grading-config state and (re-)subscribe
        // to the test case state. This component is only visible if the programming exercise has updated
        // (= dirty) test cases. The effect re-runs only when the id signal changes, matching the previous
        // ngOnChanges `previousValue !== current` guard; onCleanup unsubscribes the previous subscription.
        effect((onCleanup) => {
            const programmingExerciseId = this.programmingExerciseId();
            if (!programmingExerciseId) {
                return;
            }
            this.hasUpdatedGradingConfig.set(undefined);
            const subscription = this.programmingExerciseWebsocketService
                .getTestCaseState(programmingExerciseId)
                .pipe(
                    tap((hasUpdatedTestCases: boolean) => {
                        this.hasUpdatedGradingConfig.set(hasUpdatedTestCases);
                    }),
                    takeUntilDestroyed(this.destroyRef),
                )
                .subscribe();
            onCleanup(() => subscription.unsubscribe());
        });
    }
}
