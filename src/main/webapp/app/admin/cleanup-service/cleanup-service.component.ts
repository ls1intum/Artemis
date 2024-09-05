import { Component, OnInit } from '@angular/core';
import { faSync, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { DataCleanupService } from 'app/admin/cleanup-service/cleanup-service.service';
import { convertDateFromClient } from 'app/utils/date.utils';
import { Subject } from 'rxjs';
import { EventManager } from 'app/core/util/event-manager.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-cleanup-service',
    templateUrl: './cleanup-service.component.html',
})
export class CleanupServiceComponent implements OnInit {
    faSync = faSync;
    faTrash = faTrash;

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    constructor(
        private cleanupService: DataCleanupService,
        private eventManager: EventManager,
    ) {}

    // TODO Michal Kawka replace with API call fetching operations from the DB
    cleanupOperations: CleanupOperation[] = [
        {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: dayjs().subtract(1, 'days'),
        },
        {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: dayjs().subtract(2, 'days'),
        },
        {
            name: 'deleteNonRatedResults',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: dayjs().subtract(3, 'days'),
        },
        {
            name: 'deleteOldRatedResults',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: dayjs().subtract(4, 'days'),
        },
        {
            name: 'deleteOldSubmissionVersions',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: dayjs().subtract(5, 'days'),
        },
        {
            name: 'deleteOldFeedback',
            deleteFrom: dayjs().subtract(6, 'months'),
            deleteTo: dayjs(),
            lastExecuted: dayjs().subtract(6, 'days'),
        },
    ];

    ngOnInit(): void {
        this.refresh();
    }

    refresh(): void {
        // Implement logic to refresh the data, possibly fetching from a service
    }

    executeCleanupOperation(operation: CleanupOperation): void {
        console.log(`Executing cleanup operation: ${operation.name}`);
        const deleteFrom = convertDateFromClient(operation.deleteFrom)!;
        const deleteTo = convertDateFromClient(operation.deleteTo)!;

        const subscriptionHandler = this.handleResponse(operation);

        switch (operation.name) {
            case 'deleteOrphans':
                this.cleanupService.deleteOrphans(deleteFrom, deleteTo).subscribe(subscriptionHandler);
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
            next: () => {
                this.dialogErrorSource.next('');
                operation.lastExecuted = dayjs(); // Update lastExecuted to now
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
            },
        };
    }
}
