import { Component, signal } from '@angular/core';
import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    translationParameters = signal({});
    translationKeys = signal<GenericConfirmationTranslationKeys | undefined>(undefined);
    canBeUndone = signal(true);
    isDangerousAction = signal(false);

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
