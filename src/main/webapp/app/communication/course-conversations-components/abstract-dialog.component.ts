import { Directive, inject } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { captureException } from '@sentry/angular';

@Directive()
export abstract class AbstractDialogComponent {
    dialogRef = inject(DynamicDialogRef);
    dialogConfig = inject(DynamicDialogConfig);
    isInitialized = false;

    initialize(requiredInputs?: string[]) {
        // Apply data from DynamicDialogConfig to component properties
        if (this.dialogConfig?.data) {
            for (const [key, value] of Object.entries(this.dialogConfig.data)) {
                (this as any)[key] = value;
            }
        }
        const allInputsSet = (requiredInputs ?? []).every((input) => this[input as keyof this] !== undefined);
        if (!allInputsSet) {
            captureException('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    dismiss() {
        if (this.dialogRef) {
            this.dialogRef.close();
        }
    }
    close(result?: any) {
        if (this.dialogRef) {
            this.dialogRef.close(result);
        }
    }
}
