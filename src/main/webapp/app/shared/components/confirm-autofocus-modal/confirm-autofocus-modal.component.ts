import { Component, Input, TemplateRef, inject } from '@angular/core';
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

    @Input() title: string;
    @Input() titleTranslationParams?: Record<string, string>;
    @Input() text: string;
    @Input() translateText: boolean;
    @Input() textIsMarkdown: boolean;
    @Input() contentRef?: TemplateRef<any>;
}
