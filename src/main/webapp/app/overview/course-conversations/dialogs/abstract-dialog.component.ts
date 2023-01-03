import { Directive } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Directive()
export abstract class AbstractDialogComponent {
    isInitialized = false;

    initialize(requiredInputs?: string[]) {
        const allInputsSet = (requiredInputs ?? []).every((input) => this[input] !== undefined);
        if (!allInputsSet) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    constructor(public activeModal: NgbActiveModal) {}

    dismiss() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
    }
    close(result?: any) {
        if (this.activeModal) {
            this.activeModal.close(result);
        }
    }
}
