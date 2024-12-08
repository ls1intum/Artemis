import { Component, OnInit, inject, signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { CleanupOperation } from 'app/admin/cleanup-service/cleanup-operation.model';
import { convertDateFromServer } from 'app/utils/date.utils';
import { Subject } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { CleanupServiceExecutionRecordDTO, DataCleanupService } from 'app/admin/cleanup-service/data-cleanup.service';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CleanupOperationModalComponent } from 'app/admin/cleanup-service/cleanup-operation-modal.component';

@Component({
    selector: 'jhi-cleanup-service',
    templateUrl: './cleanup-service.component.html',
    standalone: true,
    imports: [FormDateTimePickerModule, ArtemisSharedModule, ArtemisSharedComponentModule],
})
export class CleanupServiceComponent implements OnInit {
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    private dataCleanupService: DataCleanupService = inject(DataCleanupService);
    private modalService: NgbModal = inject(NgbModal);

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
