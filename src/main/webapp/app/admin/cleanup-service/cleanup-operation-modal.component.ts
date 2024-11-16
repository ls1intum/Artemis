import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, Input, OnInit, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { CleanupCount, CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { convertDateFromServer } from 'app/utils/date.utils';
import { Observable, Observer, Subject } from 'rxjs';
import { faCheckCircle, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-cleanup-operation-modal',
    templateUrl: './cleanup-operation-modal.component.html',
    standalone: true,
    imports: [TranslateDirective, ArtemisSharedModule],
})
export class CleanupOperationModalComponent implements OnInit {
    @Input() operation: CleanupOperation;
    beforeExecutionCounts: CleanupCount;
    afterExecutionCounts: CleanupCount | undefined = undefined;

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    public activeModal: NgbActiveModal = inject(NgbActiveModal);
    private dataCleanupService: DataCleanupService = inject(DataCleanupService);

    // Icons
    faTimes = faTimes;
    faCheckCircle = faCheckCircle;

    cleanupKeys(counts: CleanupCount): (keyof CleanupCount)[] {
        return Object.keys(counts) as (keyof CleanupCount)[];
    }

    close(): void {
        this.activeModal.close();
    }

    ngOnInit(): void {
        this.fetchOperationCounts(this.operation).subscribe({
            next: (response: HttpResponse<CleanupCount>) => {
                this.beforeExecutionCounts = response.body!;
            },
            error: () => this.dialogErrorSource.next('An unexpected error occurred.'),
        });
    }

    executeCleanupOperation(): void {
        const subscriptionHandler = this.handleResponse(this.operation);

        switch (this.operation.name) {
            case 'deleteOrphans':
                this.dataCleanupService.deleteOrphans().subscribe(subscriptionHandler);
                break;
            case 'deletePlagiarismComparisons':
                this.dataCleanupService.deletePlagiarismComparisons(this.operation.deleteFrom, this.operation.deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteNonRatedResults':
                this.dataCleanupService.deleteNonRatedResults(this.operation.deleteFrom, this.operation.deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteOldRatedResults':
                this.dataCleanupService.deleteOldRatedResults(this.operation.deleteFrom, this.operation.deleteTo).subscribe(subscriptionHandler);
                break;
        }
    }

    private fetchOperationCounts(operation: CleanupOperation): Observable<HttpResponse<CleanupCount>> {
        switch (operation.name) {
            case 'deleteOrphans':
                return this.dataCleanupService.countOrphans();
            case 'deletePlagiarismComparisons':
                return this.dataCleanupService.countPlagiarismComparisons(operation.deleteFrom, operation.deleteTo);
            case 'deleteNonRatedResults':
                return this.dataCleanupService.countNonRatedResults(operation.deleteFrom, operation.deleteTo);
            case 'deleteOldRatedResults':
                return this.dataCleanupService.countOldRatedResults(operation.deleteFrom, operation.deleteTo);
            default:
                throw new Error(`Unsupported operation name: ${operation.name}`);
        }
    }

    private handleResponse(operation: CleanupOperation): Observer<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return {
            next: (response: HttpResponse<CleanupServiceExecutionRecordDTO>) => {
                this.dialogErrorSource.next('');
                const executionDateFromServer = convertDateFromServer(response.body!.executionDate);
                operation.lastExecuted = executionDateFromServer;
                this.fetchOperationCounts(operation).subscribe({
                    next: (countResponse: HttpResponse<CleanupCount>) => {
                        if (this.afterExecutionCounts) {
                            this.beforeExecutionCounts = this.afterExecutionCounts;
                        }
                        this.afterExecutionCounts = countResponse.body!;
                    },
                    error: () => {
                        this.dialogErrorSource.next('An error occurred while fetching post-execution counts.');
                    },
                });
            },
            error: (error: any) => {
                if (error instanceof HttpErrorResponse) {
                    this.dialogErrorSource.next(error.message);
                } else {
                    this.dialogErrorSource.next('An unexpected error occurred.');
                }
            },
            complete: () => {},
        };
    }

    protected readonly Object = Object;
}
