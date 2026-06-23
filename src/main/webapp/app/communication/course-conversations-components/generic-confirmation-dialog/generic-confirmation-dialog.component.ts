import { Component } from '@angular/core';
import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export interface GenericConfirmationTranslationKeys {
    titleKey: string;
    questionKey: string;
    descriptionKey: string;
    confirmButtonKey: string;
}
@Component({
    selector: 'jhi-generic-confirmation-dialog',
    templateUrl: './generic-confirmation-dialog.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe],
})
export class GenericConfirmationDialogComponent extends AbstractDialogComponent {
    translationParameters: Record<string, string> = {};
    translationKeys?: GenericConfirmationTranslationKeys;
    canBeUndone = true;
    isDangerousAction = false;

    initialize() {
        super.initialize(['translationKeys']);
    }

    clear() {
        this.dismiss();
    }

    confirm() {
        this.close(true);
    }
}
