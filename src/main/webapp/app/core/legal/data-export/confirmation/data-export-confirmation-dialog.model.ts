import { Observable } from 'rxjs';

/**
 * @deprecated This model is no longer used. The DataExportConfirmationDialogComponent
 * now uses input() signals and output() events directly.
 */
export class DataExportConfirmationDialogData {
    // error message emitted from the component delete method, that will be handled by the dialog
    // when delete method succeeded empty message is sent
    dialogError?: Observable<string>;

    userLogin?: string;

    // true if it is the dialog for an admin with additional options
    adminDialog: boolean;
}
