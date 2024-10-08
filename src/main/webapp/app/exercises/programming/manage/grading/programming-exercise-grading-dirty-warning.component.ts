import { Component, Input, OnChanges, OnDestroy, SimpleChanges, inject } from '@angular/core';
import { ProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

/**
 * Two status indicators for the test case table:
 * - Are there unsaved changes?
 * - Have test cases been changed but the student submissions were not triggered?
 */
@Component({
    selector: 'jhi-programming-exercise-grading-dirty-warning',
    template: `
        @if (hasUpdatedGradingConfig) {
            <fa-icon
                [icon]="faExclamationTriangle"
                class="text-warning"
                size="2x"
                ngbTooltip="{{ 'artemisApp.programmingExercise.configureGrading.updatedGradingConfigTooltip' | artemisTranslate }}"
            />
        }
    `,
})
export class ProgrammingExerciseGradingDirtyWarningComponent implements OnChanges, OnDestroy {
    private programmingExerciseWebsocketService = inject(ProgrammingExerciseWebsocketService);

    @Input() programmingExerciseId: number;
    @Input() hasUpdatedGradingConfigInitialValue: boolean;

    hasUpdatedGradingConfig?: boolean;
    testCaseStateSubscription: Subscription;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Set the initial updated test case value on the first change of the property.
     * Subscribe for the test case state. This component is only visible if the programming exercise has updated (= dirty) test cases.
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges) {
        // When the programming exercise changes, both set the hasUpdatedTestCases property to undefined and set up a subscription.
        if (this.programmingExerciseId && changes.programmingExerciseId.previousValue !== this.programmingExerciseId) {
            this.hasUpdatedGradingConfig = undefined;
            this.unsubscribeSubscriptions();
            this.testCaseStateSubscription = this.programmingExerciseWebsocketService
                .getTestCaseState(this.programmingExerciseId)
                .pipe(
                    tap((hasUpdatedTestCases: boolean) => {
                        this.hasUpdatedGradingConfig = hasUpdatedTestCases;
                    }),
                )
                .subscribe();
        }
        // On the first change of the updatedTestCasesInitialValue property, use it to initialize hasUpdatedTestCases.
        if (
            changes.hasUpdatedTestCasesInitialValue &&
            changes.hasUpdatedTestCasesInitialValue.currentValue !== undefined &&
            changes.hasUpdatedTestCasesInitialValue.isFirstChange()
        ) {
            this.hasUpdatedGradingConfig = this.hasUpdatedGradingConfigInitialValue;
        }
    }

    /**
     * Angular life cycle hook
     * in this case if there is a subscription for the TestCaseState, unsubscribe
     */
    ngOnDestroy(): void {
        this.unsubscribeSubscriptions();
    }

    /**
     * If there is an existing subscription, unsubscribe.
     */
    private unsubscribeSubscriptions() {
        if (this.testCaseStateSubscription) {
            this.testCaseStateSubscription.unsubscribe();
        }
    }
}
