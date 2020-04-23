import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { ProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';

/**
 * Two status indicators for the test case table:
 * - Are there unsaved changes?
 * - Have test cases been changed but the student submissions were not triggered?
 */
@Component({
    selector: 'jhi-programming-exercise-test-cases-dirty-warning',
    template: `
        <ng-container *ngIf="hasUpdatedTestCases">
            <fa-icon
                icon="exclamation-triangle"
                class="text-warning"
                size="2x"
                ngbTooltip="{{ 'artemisApp.programmingExercise.manageTestCases.updatedTestCasesTooltip' | translate }}"
            >
            </fa-icon>
        </ng-container>
    `,
})
export class ProgrammingExerciseTestCasesDirtyWarningComponent implements OnChanges, OnDestroy {
    @Input() programmingExerciseId: number;
    @Input() hasUpdatedTestCasesInitialValue: boolean;

    hasUpdatedTestCases?: boolean;
    testCaseStateSubscription: Subscription;

    constructor(private programmingExerciseWebsocketService: ProgrammingExerciseWebsocketService) {}

    /**
     * Set the initial updated test case value on the first change of the property.
     * Subscribe for the test case state. This component is only visible if the programming exercise has updated (= dirty) test cases.
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges) {
        // When the programming exercise changes, both set the hasUpdatedTestCases property to undefined and set up a subscription.
        if (this.programmingExerciseId && changes.programmingExerciseId.previousValue !== this.programmingExerciseId) {
            this.hasUpdatedTestCases = undefined;
            this.unsubscribeSubscriptions();
            this.testCaseStateSubscription = this.programmingExerciseWebsocketService
                .getTestCaseState(this.programmingExerciseId)
                .pipe(
                    tap((hasUpdatedTestCases: boolean) => {
                        this.hasUpdatedTestCases = hasUpdatedTestCases;
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
            this.hasUpdatedTestCases = this.hasUpdatedTestCasesInitialValue;
        }
    }

    ngOnDestroy(): void {
        this.unsubscribeSubscriptions();
    }

    private unsubscribeSubscriptions() {
        if (this.testCaseStateSubscription) {
            this.testCaseStateSubscription.unsubscribe();
        }
    }
}
