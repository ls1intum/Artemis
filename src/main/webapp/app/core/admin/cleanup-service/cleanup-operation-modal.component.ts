import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, OnInit, inject, input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CleanupOperation } from 'app/core/admin/cleanup-service/cleanup-operation.model';
import { CleanupCount, DataCleanupService } from 'app/core/admin/cleanup-service/data-cleanup.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { Observable, Subject } from 'rxjs';
import { faCheckCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-cleanup-operation-modal',
    templateUrl: './cleanup-operation-modal.component.html',
    imports: [TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe, FontAwesomeModule],
})
export class CleanupOperationModalComponent implements OnInit {
    operation = input.required<CleanupOperation>();
    counts: CleanupCount = { totalCount: 0 };
    operationExecuted = false;

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    public activeModal: NgbActiveModal = inject(NgbActiveModal);
    private dataCleanupService: DataCleanupService = inject(DataCleanupService);

    faTimes = faTimes;
    faCheckCircle = faCheckCircle;

    /**
     * Fetch keys from the CleanupCount object for iteration.
     */
    get cleanupKeys(): (keyof CleanupCount)[] {
        return Object.keys(this.counts) as (keyof CleanupCount)[];
    }

    /**
     * Close the modal.
     */
    close(): void {
        this.activeModal.close();
    }

    /**
     * Initialize component: fetch initial counts for the operation.
     */
    ngOnInit(): void {
        this.updateCounts();
    }

    /**
     * Execute the cleanup operation and update counts afterward.
     */
    executeCleanupOperation(): void {
        const operationHandler = {
            next: () => {
                this.operationExecuted = true;
                this.updateCounts();
            },
            error: (error: any) => {
                this.dialogErrorSource.next(error instanceof HttpErrorResponse ? error.message : 'An unexpected error occurred.');
            },
        };

        switch (this.operation().name) {
            case 'deleteOrphans':
                this.dataCleanupService.deleteOrphans().subscribe(operationHandler);
                break;
            case 'deletePlagiarismComparisons':
                this.dataCleanupService.deletePlagiarismComparisons(this.operation().deleteFrom, this.operation().deleteTo).subscribe(operationHandler);
                break;
            case 'deleteNonRatedResults':
                this.dataCleanupService.deleteNonRatedResults(this.operation().deleteFrom, this.operation().deleteTo).subscribe(operationHandler);
                break;
            case 'deleteOldRatedResults':
                this.dataCleanupService.deleteOldRatedResults(this.operation().deleteFrom, this.operation().deleteTo).subscribe(operationHandler);
                break;
            case 'deleteOldSubmissionVersions':
                this.dataCleanupService.deleteOldSubmissionVersions(this.operation().deleteFrom, this.operation().deleteTo).subscribe(operationHandler);
                break;
        }
    }

    /**
     * Fetch counts for the operation.
     */
    private fetchCounts(): Observable<HttpResponse<CleanupCount>> {
        switch (this.operation().name) {
            case 'deleteOrphans':
                return this.dataCleanupService.countOrphans();
            case 'deletePlagiarismComparisons':
                return this.dataCleanupService.countPlagiarismComparisons(this.operation().deleteFrom, this.operation().deleteTo);
            case 'deleteNonRatedResults':
                return this.dataCleanupService.countNonRatedResults(this.operation().deleteFrom, this.operation().deleteTo);
            case 'deleteOldRatedResults':
                return this.dataCleanupService.countOldRatedResults(this.operation().deleteFrom, this.operation().deleteTo);
            case 'deleteOldSubmissionVersions':
                return this.dataCleanupService.countOldSubmissionVersions(this.operation().deleteFrom, this.operation().deleteTo);
            default:
                throw new Error(`Unsupported operation: ${this.operation().name}`);
        }
    }

    /**
     * Fetch updated counts after operation execution.
     */
    private updateCounts(): void {
        this.fetchCounts().subscribe({
            next: (response: HttpResponse<CleanupCount>) => {
                this.counts = response.body!;
            },
            error: () => this.dialogErrorSource.next('An error occurred while fetching updated counts.'),
        });
    }

    /**
     * Getter to check if there are any entries to delete.
     */
    get hasEntriesToDelete(): boolean {
        return Object.values(this.counts).some((count) => count > 0);
    }
}
