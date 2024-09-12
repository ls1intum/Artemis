import { Component, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/cleanup-service.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { Subject } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-cleanup-service',
    templateUrl: './cleanup-service.component.html',
})
export class CleanupServiceComponent implements OnInit {
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    constructor(private cleanupService: DataCleanupService) {}

    cleanupOperations: CleanupOperation[] = [
        {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
        },
        {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
        },
        {
            name: 'deleteNonRatedResults',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
        },
        {
            name: 'deleteOldRatedResults',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
        }
    ];

    ngOnInit(): void {
        this.loadLastExecutions();
    }

    loadLastExecutions(): void {
        this.cleanupService.getLastExecutions().subscribe((executionRecordsBody: HttpResponse<CleanupServiceExecutionRecordDTO[]>) => {
            const executionRecords = executionRecordsBody.body!;
            if (executionRecords && executionRecords.length > 0) {
                this.cleanupOperations.forEach((operation, index) => {
                    const executionRecord = executionRecords[index];
                    if (executionRecord && executionRecord.executionDate) {
                        operation.lastExecuted = convertDateFromServer(executionRecord.executionDate);
                    }
                });
            }
        });
    }

    executeCleanupOperation(operation: CleanupOperation): void {
        const deleteFrom = convertDateFromClient(operation.deleteFrom)!;
        const deleteTo = convertDateFromClient(operation.deleteTo)!;

        const subscriptionHandler = this.handleResponse(operation);

        switch (operation.name) {
            case 'deleteOrphans':
                this.cleanupService.deleteOrphans().subscribe(subscriptionHandler);
                break;
            case 'deletePlagiarismComparisons':
                this.cleanupService.deletePlagiarismComparisons(deleteFrom, deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteNonRatedResults':
                this.cleanupService.deleteNonRatedResults(deleteFrom, deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteOldRatedResults':
                this.cleanupService.deleteOldRatedResults(deleteFrom, deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteOldSubmissionVersions':
                this.cleanupService.deleteOldSubmissionVersions(deleteFrom, deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteOldFeedback':
                this.cleanupService.deleteOldFeedback(deleteFrom, deleteTo).subscribe(subscriptionHandler);
                break;
        }
    }

    private handleResponse(operation: CleanupOperation) {
        return {
            next: (response: HttpResponse<CleanupServiceExecutionRecordDTO>) => {
                this.dialogErrorSource.next('');
                const executionDateFromServer = convertDateFromServer(response.body!.executionDate);
                operation.lastExecuted = executionDateFromServer;
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
            },
        };
    }

    areDatesValid(operation: CleanupOperation): boolean {
        return operation.deleteFrom && operation.deleteTo && dayjs(operation.deleteTo).isAfter(dayjs(operation.deleteFrom));
    }
}
