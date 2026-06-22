import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { HttpResponse } from '@angular/common/http';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { CleanupOperationModalComponent } from 'app/admin/cleanup-service/cleanup-operation-modal.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';

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
        TableModule,
        ButtonModule,
        DatePickerModule,
        FaIconComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CleanupServiceComponent implements OnInit {
    private readonly dataCleanupService = inject(DataCleanupService);

    protected readonly faTrash = faTrash;

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
        {
            name: 'deleteOldSubmissionVersions',
            deleteFrom: dayjs().subtract(12, 'months'),
            deleteTo: dayjs().subtract(6, 'months'),
            lastExecuted: undefined,
            datesValid: signal(true),
        },
    ]);

    ngOnInit(): void {
        this.loadLastExecutions();
    }

    loadLastExecutions(): void {
        this.dataCleanupService.getLastExecutions().subscribe((executionRecordsBody: HttpResponse<CleanupServiceExecutionRecordDTO[]>) => {
            const executionRecords = executionRecordsBody.body!;
            if (executionRecords && executionRecords.length > 0) {
                this.cleanupOperations.update((operations) =>
                    operations.map((operation, index) => {
                        const executionRecord = executionRecords[index];
                        if (executionRecord && executionRecord.executionDate) {
                            return { ...operation, lastExecuted: convertDateFromServer(executionRecord.executionDate) };
                        }
                        return operation;
                    }),
                );
            }
        });
    }

    validateDates(operation: CleanupOperation): void {
        const datesValid = operation.deleteFrom && operation.deleteTo && dayjs(operation.deleteTo).isAfter(dayjs(operation.deleteFrom));
        operation.datesValid.set(datesValid);
    }

    /**
     * Per-operation cache of the native {@link Date} objects handed to the PrimeNG datepickers.
     *
     * The picker is bound via `[ngModel]="toDate(...)"`. A template method binding is re-evaluated
     * on every change-detection pass, and a naive `value.toDate()` allocates a brand-new `Date`
     * each time. PrimeNG compares the incoming model by reference, so a new instance forces it to
     * re-run `writeValue` -> `updateUI` -> `createMonths` (a full 6x7 month-grid rebuild) on each
     * pass. With two datepickers per cleanup-operation row this dominated the render
     * cost. We therefore memoize the converted `Date` per dayjs value and only allocate a new one
     * when the underlying timestamp actually changes, giving the picker a stable reference.
     */
    private readonly dateCache = new WeakMap<CleanupOperation, { fromMs?: number; fromDate?: Date; toMs?: number; toDate?: Date }>();

    /**
     * Convert a dayjs value (UTC-backed) to a native Date for the PrimeNG datepicker,
     * which works with local Date objects. The result is memoized per operation/field so the
     * `[ngModel]` binding receives a stable reference across change-detection passes (see
     * {@link dateCache}).
     */
    toDate(operation: CleanupOperation, field: 'from' | 'to'): Date | undefined {
        const value = field === 'from' ? operation.deleteFrom : operation.deleteTo;
        if (!value) {
            return undefined;
        }
        const ms = value.valueOf();
        let entry = this.dateCache.get(operation);
        if (!entry) {
            entry = {};
            this.dateCache.set(operation, entry);
        }
        if (field === 'from') {
            if (entry.fromMs !== ms || !entry.fromDate) {
                entry.fromMs = ms;
                entry.fromDate = value.toDate();
            }
            return entry.fromDate;
        }
        if (entry.toMs !== ms || !entry.toDate) {
            entry.toMs = ms;
            entry.toDate = value.toDate();
        }
        return entry.toDate;
    }

    /**
     * Handle the start date change emitted by the PrimeNG datepicker (a local Date),
     * convert it back to dayjs and re-validate the operation.
     */
    onDeleteFromChange(operation: CleanupOperation, date: Date | undefined): void {
        if (date) {
            operation.deleteFrom = dayjs(date);
        }
        this.validateDates(operation);
    }

    /**
     * Handle the end date change emitted by the PrimeNG datepicker (a local Date),
     * convert it back to dayjs and re-validate the operation.
     */
    onDeleteToChange(operation: CleanupOperation, date: Date | undefined): void {
        if (date) {
            operation.deleteTo = dayjs(date);
        }
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
