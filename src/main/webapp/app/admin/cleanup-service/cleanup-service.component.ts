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

    cleanupOperations: CleanupOperation[] = [
        {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(30, 'days'), // 30 days ago
            deleteTo: dayjs().subtract(10, 'days'), // 10 days ago
            lastExecuted: dayjs().subtract(1, 'days'), // 1 day ago
        },
        {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(60, 'days'), // 60 days ago
            deleteTo: dayjs().subtract(20, 'days'), // 20 days ago
            lastExecuted: dayjs().subtract(2, 'days'), // 2 days ago
        },
        {
            name: 'deleteNonRatedResults',
            deleteFrom: dayjs().subtract(90, 'days'), // 90 days ago
            deleteTo: dayjs().subtract(30, 'days'), // 30 days ago
            lastExecuted: dayjs().subtract(3, 'days'), // 3 days ago
        },
        {
            name: 'deleteOldRatedResults',
            deleteFrom: dayjs().subtract(120, 'days'), // 120 days ago
            deleteTo: dayjs().subtract(40, 'days'), // 40 days ago
            lastExecuted: dayjs().subtract(4, 'days'), // 4 days ago
        },
        {
            name: 'deleteOldSubmissionVersions',
            deleteFrom: dayjs().subtract(150, 'days'), // 150 days ago
            deleteTo: dayjs().subtract(50, 'days'), // 50 days ago
            lastExecuted: dayjs().subtract(5, 'days'), // 5 days ago
        },
        {
            name: 'deleteOldFeedback',
            deleteFrom: dayjs().subtract(180, 'days'), // 180 days ago
            deleteTo: dayjs().subtract(60, 'days'), // 60 days ago
            lastExecuted: dayjs().subtract(6, 'days'), // 6 days ago
        },
    ];

    // constructor(private cleanupService: CleanupService) {}

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

        const subscriptionHandler = this.handleResponse();

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
            default:
                console.warn(`Unknown operation: ${operation.name}`);
        }
    }

    private handleResponse() {
        return {
            next: () => {
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
            },
        };
    }
}
