import { Component, TemplateRef, inject, input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-confirm-modal',
    templateUrl: './confirm-autofocus-modal.component.html',
    imports: [NgTemplateOutlet, TranslateDirective, ArtemisTranslatePipe],
})
export class ConfirmAutofocusModalComponent {
    modal = inject(NgbActiveModal);

    title = input.required<string>();
    titleTranslationParams = input<Record<string, string> | undefined>(undefined);
    text = input.required<string>();
    translateText = input.required<boolean>();
    textIsMarkdown = input.required<boolean>();
    contentRef = input<TemplateRef<any> | undefined>(undefined);
    confirmDisabled = input(false);
}
