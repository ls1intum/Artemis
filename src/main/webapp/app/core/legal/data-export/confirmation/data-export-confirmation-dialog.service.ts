import { Injectable, inject } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';

/**
 * @deprecated The DataExportConfirmationDialogService is no longer needed.
 * The DataExportRequestButtonDirective (now a component) directly embeds
 * the DataExportConfirmationDialogComponent using PrimeNG Dialog.
 * This service is kept as a stub for backward compatibility.
 */
@Injectable({ providedIn: 'root' })
export class DataExportConfirmationDialogService {
    private readonly alertService = inject(AlertService);

    /**
     * @deprecated Use the DataExportRequestButtonDirective component directly instead.
     * The dialog is now embedded in the component's template.
     */
    openConfirmationDialog(): void {
        this.alertService.closeAll();
    }
}
