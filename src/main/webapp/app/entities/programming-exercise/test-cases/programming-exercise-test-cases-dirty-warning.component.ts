import { Component, HostBinding, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { ProgrammingExerciseWebsocketService } from 'app/entities/programming-exercise/services/programming-exercise-websocket.service';
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
            <fa-icon icon="exclamation-triangle" class="text-warning" size="2x" ngbTooltip="'test'"> </fa-icon>
        </ng-container>
    `,
})
export class ProgrammingExerciseTestCasesDirtyWarningComponent implements OnChanges, OnDestroy {
    @Input() programmingExerciseId: number;

    hasUpdatedTestCases: boolean;
    testCaseStateSubscription: Subscription;

    constructor(private programmingExerciseWebsocketService: ProgrammingExerciseWebsocketService) {}

    /**
     * Subscribe for the test case state. This component is only visible if the programming exercise has updated (= dirty) test cases.
     *
     * @param changes
     */
    ngOnChanges(changes: SimpleChanges) {
        if (this.programmingExerciseId && changes.programmingExerciseId.previousValue !== this.programmingExerciseId) {
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
