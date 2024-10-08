import { Component, Input } from '@angular/core';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

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
export class GenericConfirmationDialogComponent extends AbstractDialogComponent {
    @Input() translationParameters = {};
    @Input() translationKeys: GenericConfirmationTranslationKeys;
    @Input() canBeUndone = true;
    @Input() isDangerousAction = false;

    initialize() {
        super.initialize(['translationKeys']);
    }

    clear() {
        this.dismiss();
    }

    confirm() {
        this.close();
    }
}
