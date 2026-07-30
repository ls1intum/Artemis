import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CleanupOperation, OperationName } from 'app/admin/cleanup-service/cleanup-operation.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { CleanupOperationModalComponent } from 'app/admin/cleanup-service/cleanup-operation-modal.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiDatePickerComponent } from 'app/shared-ui/tum-ui/date-picker/tum-ui-date-picker.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * Admin component for managing data cleanup operations.
 * Allows scheduling and executing various cleanup tasks like deleting orphaned entities.
 */
@Component({
    selector: 'jhi-cleanup-service',
    templateUrl: './cleanup-service.component.html',
    imports: [
        ArtemisTranslatePipe,
        HelpIconComponent,
        TranslateDirective,
        FormsModule,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        CleanupOperationModalComponent,
        TumUiTableDirective,
        TumUiButtonDirective,
        TumUiDatePickerComponent,
        FaIconComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CleanupServiceComponent implements OnInit {
    private readonly dataCleanupService = inject(DataCleanupService);
    private readonly alertService = inject(AlertService);

    protected readonly faTrash = faTrash;

    // Maps each client operation to the server CleanupJobType.label() it corresponds to. The names differ for
    // several jobs (e.g. 'deleteOldRatedResults' -> server 'deleteRatedResults'), so the execution records must
    // be matched by this explicit job type, NOT by array position (which silently mislabels dates if the server
    // ever changes the order or the set of returned job types).
    private readonly serverJobTypeByName: Record<OperationName, string> = {
        deleteOrphans: 'deleteOrphans',
        deletePlagiarismComparisons: 'deletePlagiarismComparisons',
        deleteNonRatedResults: 'deleteNonRatedResults',
        deleteOldRatedResults: 'deleteRatedResults',
        deleteOldSubmissionVersions: 'deleteSubmissionVersions',
        deleteOldFeedback: 'deleteFeedback',
    };

    /** Whether the cleanup operation modal is visible */
    showCleanupModal = signal<boolean>(false);

    /** The currently selected operation for the modal */
    selectedOperation = signal<CleanupOperation | undefined>(undefined);

    /** Cleanup operations data - uses signal for reactivity */
    readonly cleanupOperations = signal<CleanupOperation[]>([
        {
            name: 'deleteOrphans',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        {
            name: 'deletePlagiarismComparisons',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        {
            name: 'deleteNonRatedResults',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        {
            name: 'deleteOldRatedResults',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
        {
            name: 'deleteOldSubmissionVersions',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
            deleteFromValid: signal(true),
            deleteToValid: signal(true),
        },
    ]);

    ngOnInit(): void {
        this.loadLastExecutions();
    }

    loadLastExecutions(): void {
        this.dataCleanupService.getLastExecutions().subscribe({
            next: (response) => {
                const executionRecords = response.body ?? [];
                // Match by server job type, not array position (see serverJobTypeByName).
                const executionDateByJobType = new Map(executionRecords.map((record) => [record.jobType, record.executionDate]));
                this.cleanupOperations.update((operations) =>
                    operations.map((operation) => {
                        const executionDate = executionDateByJobType.get(this.serverJobTypeByName[operation.name]);
                        return executionDate ? cloneWith(operation, { lastExecuted: convertDateFromServer(executionDate) }) : operation;
                    }),
                );
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    validateDates(operation: CleanupOperation): void {
        const datesValid = !!(operation.deleteFrom && operation.deleteTo && dayjs(operation.deleteTo).isAfter(dayjs(operation.deleteFrom)));
        operation.datesValid.set(datesValid);
    }

    onDeleteFromChange(operation: CleanupOperation, value: dayjs.Dayjs | undefined): void {
        operation.deleteFrom = value;
        this.validateDates(operation);
    }

    onDeleteToChange(operation: CleanupOperation, value: dayjs.Dayjs | undefined): void {
        operation.deleteTo = value;
        this.validateDates(operation);
    }

    /**
     * Handles displaying the modal with operation details and counts.
     */
    openCleanupOperationModal(operation: CleanupOperation): void {
        this.selectedOperation.set(operation);
        this.showCleanupModal.set(true);
    }
}
