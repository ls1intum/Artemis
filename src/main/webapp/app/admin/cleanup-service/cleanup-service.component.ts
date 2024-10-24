import { Component, OnInit, inject, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { convertDateFromServer } from 'app/utils/date.utils';
import { Subject } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { Observer } from 'rxjs';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-cleanup-service',
    templateUrl: './cleanup-service.component.html',
    standalone: true,
    imports: [FormDateTimePickerModule, ArtemisSharedModule],
})
export class CleanupServiceComponent implements OnInit {
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    private dataCleanupService: DataCleanupService = inject(DataCleanupService);

    cleanupOperations: CleanupOperation[] = [
        {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
        },
        {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
        },
        {
            name: 'deleteNonRatedResults',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
        },
        {
            name: 'deleteOldRatedResults',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
        },
    ];

    ngOnInit(): void {
        this.loadLastExecutions();
    }

    loadLastExecutions(): void {
        this.dataCleanupService.getLastExecutions().subscribe((executionRecordsBody: HttpResponse<CleanupServiceExecutionRecordDTO[]>) => {
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
        const subscriptionHandler = this.handleResponse(operation);

        switch (operation.name) {
            case 'deleteOrphans':
                this.dataCleanupService.deleteOrphans().subscribe(subscriptionHandler);
                break;
            case 'deletePlagiarismComparisons':
                this.dataCleanupService.deletePlagiarismComparisons(operation.deleteFrom, operation.deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteNonRatedResults':
                this.dataCleanupService.deleteNonRatedResults(operation.deleteFrom, operation.deleteTo).subscribe(subscriptionHandler);
                break;
            case 'deleteOldRatedResults':
                this.dataCleanupService.deleteOldRatedResults(operation.deleteFrom, operation.deleteTo).subscribe(subscriptionHandler);
                break;
        }
    }

    private handleResponse(operation: CleanupOperation): Observer<HttpResponse<CleanupServiceExecutionRecordDTO>> {
        return {
            next: (response: HttpResponse<CleanupServiceExecutionRecordDTO>) => {
                this.dialogErrorSource.next('');
                const executionDateFromServer = convertDateFromServer(response.body!.executionDate);
                operation.lastExecuted = executionDateFromServer;
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

    validateDates(operation: CleanupOperation): void {
        const datesValid = operation.deleteFrom && operation.deleteTo && dayjs(operation.deleteTo).isAfter(dayjs(operation.deleteFrom));
        operation.datesValid.set(datesValid);
    }
}
