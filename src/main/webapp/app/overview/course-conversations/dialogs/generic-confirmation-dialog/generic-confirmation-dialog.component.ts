import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

export interface GenericConfirmationTranslationKeys {
    titleKey: string;
    questionKey: string;
    descriptionKey: string;
    confirmButtonKey: string;
}
@Component({
    selector: 'jhi-generic-confirmation-dialog',
    templateUrl: './generic-confirmation-dialog.component.html',
})
export class GenericConfirmationDialog {
    @Input()
    translationParameters = {};

    @Input()
    translationKeys: GenericConfirmationTranslationKeys;

    @Input()
    canBeUndone = true;

    constructor(private activeModal: NgbActiveModal) {}

    isInitialized = false;

    initialize() {
        if (!this.translationKeys) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

    clear() {
        this.activeModal.dismiss();
    }

    confirm() {
        this.activeModal.close();
    }
}
