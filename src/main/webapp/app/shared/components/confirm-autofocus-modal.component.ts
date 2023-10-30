import { Component, Input, TemplateRef } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-confirm-modal',
    templateUrl: './confirm-autofocus-modal.component.html',
})
export class ConfirmAutofocusModalComponent {
    @Input() title: string;
    @Input() titleTranslationParams?: Record<string, string>;
    @Input() text: string;
    @Input() translateText: boolean;
    @Input() textIsMarkdown: boolean;
    @Input() contentRef?: TemplateRef<any>;

    constructor(public modal: NgbActiveModal) {}
}
