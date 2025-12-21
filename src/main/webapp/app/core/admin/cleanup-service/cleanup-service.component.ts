import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CleanupOperation } from 'app/core/admin/cleanup-service/cleanup-operation.model';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { Subject } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/core/admin/cleanup-service/data-cleanup.service';

import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CleanupOperationModalComponent } from 'app/core/admin/cleanup-service/cleanup-operation-modal.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

/**
 * Admin component for managing data cleanup operations.
 * Allows scheduling and executing various cleanup tasks like deleting orphaned entities.
 */
@Component({
    selector: 'jhi-cleanup-service',
    templateUrl: './cleanup-service.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormDateTimePickerComponent, ArtemisTranslatePipe, HelpIconComponent, TranslateDirective, FormsModule, ArtemisDatePipe],
})
export class CleanupServiceComponent implements OnInit {
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    private readonly dataCleanupService = inject(DataCleanupService);
    private readonly modalService = inject(NgbModal);

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
     * Handles displaying the modal with operation details and counts.
     */
    openCleanupOperationModal(operation: CleanupOperation): void {
        const modalRef = this.modalService.open(CleanupOperationModalComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.operation = signal<CleanupOperation>(operation);
    }
}
