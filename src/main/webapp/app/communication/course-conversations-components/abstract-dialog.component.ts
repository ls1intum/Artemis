import { Directive, OnInit, WritableSignal, inject, isSignal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { captureException } from '@sentry/angular';

function isWritableSignal(value: unknown): value is WritableSignal<unknown> {
    return isSignal(value) && typeof (value as { set?: unknown }).set === 'function';
}

@Directive()
export abstract class AbstractDialogComponent implements OnInit {
    dialogRef = inject(DynamicDialogRef, { optional: true });
    dialogConfig = inject(DynamicDialogConfig, { optional: true });
    isInitialized = false;

    ngOnInit() {
        this.initialize();
    }

    initialize(requiredInputs?: string[]) {
        // Apply data from DynamicDialogConfig to component properties
        if (this.dialogConfig?.data) {
            // Reflect over the component's own signal inputs / plain properties by key. There is no static type for
            // "this component's dynamic keys", so a minimal index-signature view is used instead of `any`.
            const self = this as Record<string, unknown>;
            for (const [key, value] of Object.entries(this.dialogConfig.data)) {
                if (!(key in this)) {
                    continue;
                }
                const prop = self[key];
                if (isWritableSignal(prop)) {
                    prop.set(value);
                } else if (typeof prop !== 'function') {
                    self[key] = value;
                } else {
                    // Keep callbacks and other non-input data on dialogConfig.data to avoid replacing component methods.
                    continue;
                }
            }
        }
        const allInputsSet = (requiredInputs ?? []).every((inputKey) => {
            const prop = (this as Record<string, unknown>)[inputKey];
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
        this.dialogRef?.destroy();
    }

    close(result?: unknown) {
        this.dialogRef?.close(result);
    }
}
