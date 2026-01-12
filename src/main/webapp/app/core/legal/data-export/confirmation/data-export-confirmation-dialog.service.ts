import { Injectable, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { from } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { DataExportConfirmationDialogData } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.model';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';

@Injectable({ providedIn: 'root' })
export class DataExportConfirmationDialogService {
    private readonly modalService = inject(NgbModal);
    private readonly alertService = inject(AlertService);

    modalRef: NgbModalRef | null;

    /**
     * Opens data export confirmation dialog
     * @param dataExportConfirmationDialogData data that is used in dialog
     */
    openConfirmationDialog(dataExportConfirmationDialogData: DataExportConfirmationDialogData): void {
        this.alertService.closeAll();
        this.modalRef = this.modalService.open(DataExportConfirmationDialogComponent, { size: 'lg', backdrop: 'static' });
        this.modalRef.componentInstance.expectedLogin.set(dataExportConfirmationDialogData.userLogin ?? '');
        this.modalRef.componentInstance.adminDialog.set(dataExportConfirmationDialogData.adminDialog);
        this.modalRef.componentInstance.dataExportRequest = dataExportConfirmationDialogData.dataExportRequest;
        this.modalRef.componentInstance.dataExportRequestForAnotherUser = dataExportConfirmationDialogData.dataExportRequestForAnotherUser;
        this.modalRef.componentInstance.dialogError = dataExportConfirmationDialogData.dialogError;

        from(this.modalRef.result).subscribe(() => (this.modalRef = null));
    }
}
