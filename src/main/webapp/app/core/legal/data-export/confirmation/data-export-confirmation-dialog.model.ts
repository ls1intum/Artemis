import { OutputEmitterRef } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Data that will be passed to the data export confirmation dialog component
 */
export class DataExportConfirmationDialogData {
    // error message emitted from the component delete method, that will be handled by the dialog
    // when delete method succeeded empty message is sent
    dialogError?: Observable<string>;

    userLogin?: string;

    // true if it is the dialog for an admin with additional options
    adminDialog: boolean;
    // output event passed to the confirmation dialog
    dataExportRequest: OutputEmitterRef<void>;

    //output event passed to the confirmation dialog, emitting the entered login
    dataExportRequestForAnotherUser: OutputEmitterRef<string>;
}
