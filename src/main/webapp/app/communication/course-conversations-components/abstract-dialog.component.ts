import { Directive, WritableSignal, inject, isSignal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { captureException } from '@sentry/angular';

function isWritableSignal(value: unknown): value is WritableSignal<unknown> {
    return isSignal(value) && typeof (value as any).set === 'function';
}

@Directive()
export abstract class AbstractDialogComponent {
    dialogRef = inject(DynamicDialogRef);
    dialogConfig = inject(DynamicDialogConfig);
    isInitialized = false;

    initialize(requiredInputs?: string[]) {
        // Apply data from DynamicDialogConfig to component properties
        if (this.dialogConfig?.data) {
            for (const [key, value] of Object.entries(this.dialogConfig.data)) {
                const prop = (this as any)[key];
                if (isWritableSignal(prop)) {
                    prop.set(value);
                } else {
                    (this as any)[key] = value;
                }
            }
        }
        const allInputsSet = (requiredInputs ?? []).every((inputKey) => {
            const prop = (this as any)[inputKey];
            if (isSignal(prop)) {
                return prop() !== undefined;
            }
            return prop !== undefined;
        });
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
