import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, signal, untracked } from '@angular/core';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { CleanupCount, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

import { Observable, Subject, Subscription, finalize } from 'rxjs';
import { faCheckCircle, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

/**
 * Modal component for executing and monitoring cleanup operations.
 * Shows counts of entities to be cleaned up and allows executing the operation.
 */
@Component({
    selector: 'jhi-cleanup-operation-modal',
    templateUrl: './cleanup-operation-modal.component.html',
    imports: [TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe, FontAwesomeModule, DialogModule, ButtonModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CleanupOperationModalComponent {
    /** Whether the dialog is visible */
    readonly visible = model<boolean>(false);

    /** The cleanup operation to execute */
    readonly operation = input.required<CleanupOperation>();

    /** Counts of entities to be cleaned up */
    readonly counts = signal<CleanupCount>({ totalCount: 0 });

    /** Whether the operation has been executed */
    readonly operationExecuted = signal(false);

    /** Whether a cleanup operation is currently being executed. */
    readonly operationExecuting = signal(false);

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    /** The in-flight count request, so it can be superseded/cancelled to avoid stale, out-of-order responses. */
    private countSubscription?: Subscription;

    /** The in-flight cleanup request, so reopening the persistent modal cannot apply a stale completion to another operation. */
    private executionSubscription?: Subscription;

    private readonly dataCleanupService = inject(DataCleanupService);

    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faCheckCircle = faCheckCircle;

    /** Keys from the CleanupCount object for iteration */
    readonly cleanupKeys = computed(() => Object.keys(this.counts()) as (keyof CleanupCount)[]);

    /** Computed property to check if there are any entries to delete */
    readonly hasEntriesToDelete = computed(() => Object.values(this.counts()).some((count) => count > 0));

    constructor() {
        effect(() => {
            if (this.visible()) {
                untracked(() => {
                    // Reset per-open state so reopening the modal for a different operation does not flash the
                    // previous run's result icons/counts (operationExecuted is only ever set true, and counts
                    // refresh asynchronously): start clean, then fetch this operation's counts.
                    this.executionSubscription?.unsubscribe();
                    this.operationExecuting.set(false);
                    this.operationExecuted.set(false);
                    this.counts.set({ totalCount: 0 });
                    this.updateCounts();
                });
            } else {
                // This modal instance persists across opens (its host @if never tears it down), so cancel any
                // in-flight count request on close: otherwise a late response could overwrite the counts of the
                // next operation opened here.
                this.countSubscription?.unsubscribe();
                this.executionSubscription?.unsubscribe();
                this.operationExecuting.set(false);
            }
        });
    }

    /**
     * Close the modal.
     */
    close(): void {
        this.visible.set(false);
    }

    /**
     * Execute the cleanup operation and update counts afterward.
     */
    executeCleanupOperation(): void {
        if (this.operationExecuting()) {
            return;
        }

        const operation = this.operation();
        this.operationExecuting.set(true);
        const operationHandler = {
            next: () => {
                if (!this.visible() || this.operation() !== operation) {
                    return;
                }
                this.operationExecuted.set(true);
                this.updateCounts();
            },
            error: (error: unknown) => {
                if (this.visible() && this.operation() === operation) {
                    this.dialogErrorSource.next(error instanceof HttpErrorResponse ? error.message : 'An unexpected error occurred.');
                }
            },
        };

        // Range operations are only reachable once validateDates has confirmed both dates are set.
        const deleteFrom = operation.deleteFrom!;
        const deleteTo = operation.deleteTo!;
        let executionRequest: Observable<unknown>;
        switch (operation.name) {
            case 'deleteOrphans':
                executionRequest = this.dataCleanupService.deleteOrphans();
                break;
            case 'deletePlagiarismComparisons':
                executionRequest = this.dataCleanupService.deletePlagiarismComparisons(deleteFrom, deleteTo);
                break;
            case 'deleteNonRatedResults':
                executionRequest = this.dataCleanupService.deleteNonRatedResults(deleteFrom, deleteTo);
                break;
            case 'deleteOldRatedResults':
                executionRequest = this.dataCleanupService.deleteOldRatedResults(deleteFrom, deleteTo);
                break;
            case 'deleteOldSubmissionVersions':
                executionRequest = this.dataCleanupService.deleteOldSubmissionVersions(deleteFrom, deleteTo);
                break;
            default:
                this.operationExecuting.set(false);
                throw new Error(`Unsupported operation: ${operation.name}`);
        }
        this.executionSubscription = executionRequest.pipe(finalize(() => this.operationExecuting.set(false))).subscribe(operationHandler);
    }

    /**
     * Fetch counts for the operation.
     */
    private fetchCounts(): Observable<HttpResponse<CleanupCount>> {
        const operation = this.operation();
        // Range operations are only reachable once validateDates has confirmed both dates are set.
        const deleteFrom = operation.deleteFrom!;
        const deleteTo = operation.deleteTo!;
        switch (operation.name) {
            case 'deleteOrphans':
                return this.dataCleanupService.countOrphans();
            case 'deletePlagiarismComparisons':
                return this.dataCleanupService.countPlagiarismComparisons(deleteFrom, deleteTo);
            case 'deleteNonRatedResults':
                return this.dataCleanupService.countNonRatedResults(deleteFrom, deleteTo);
            case 'deleteOldRatedResults':
                return this.dataCleanupService.countOldRatedResults(deleteFrom, deleteTo);
            case 'deleteOldSubmissionVersions':
                return this.dataCleanupService.countOldSubmissionVersions(deleteFrom, deleteTo);
            default:
                throw new Error(`Unsupported operation: ${operation.name}`);
        }
    }

    /**
     * Fetch updated counts after operation execution.
     */
    private updateCounts(): void {
        // Supersede any previous, still-pending count request so an out-of-order response cannot overwrite the
        // counts of the operation currently shown.
        this.countSubscription?.unsubscribe();
        this.countSubscription = this.fetchCounts().subscribe({
            next: (response: HttpResponse<CleanupCount>) => {
                this.counts.set(response.body!);
            },
            error: () => {
                this.dialogErrorSource.next('An error occurred while fetching updated counts.');
            },
        });
    }
}
