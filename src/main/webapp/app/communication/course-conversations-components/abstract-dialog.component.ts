import { Directive, OnInit, WritableSignal, inject, isSignal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { captureException } from '@sentry/angular';

function isWritableSignal(value: unknown): value is WritableSignal<unknown> {
    return isSignal(value) && typeof (value as any).set === 'function';
}

@Directive()
export abstract class AbstractDialogComponent implements OnInit {
    dialogRef = inject(DynamicDialogRef, { optional: true });
    dialogConfig = inject(DynamicDialogConfig, { optional: true });
    activeModal = inject(NgbActiveModal, { optional: true });
    isInitialized = false;

    ngOnInit() {
        this.initialize();
    }

    initialize(requiredInputs?: string[]) {
        // Apply data from DynamicDialogConfig to component properties
        if (this.dialogConfig?.data) {
            for (const [key, value] of Object.entries(this.dialogConfig.data)) {
                if (!(key in this)) {
                    continue;
                }
                const prop = (this as any)[key];
                if (isWritableSignal(prop)) {
                    prop.set(value);
                } else if (typeof prop !== 'function') {
                    (this as any)[key] = value;
                } else {
                    // Keep callbacks and other non-input data on dialogConfig.data to avoid replacing component methods.
                    continue;
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
            this.dialogRef.destroy();
        } else if (this.activeModal) {
            this.activeModal.dismiss();
        }
    }
    close(result?: any) {
        if (this.dialogRef) {
            this.dialogRef.close(result);
        } else if (this.activeModal) {
            this.activeModal.close(result);
        }
    }
}
