import { Component, Input, TemplateRef, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-confirm-modal',
    templateUrl: './confirm-autofocus-modal.component.html',
    imports: [NgTemplateOutlet, TranslateDirective, ArtemisTranslatePipe],
})
// NOTE: This shared dialog intentionally still uses NgbActiveModal and @Input decorators. It is opened
// via `NgbModal.open(ConfirmAutofocusModalComponent)` + `modalRef.componentInstance.* = ...` by several
// callers, two of which live in the deferred programming/** carve-out (and others in separate migration
// groups: atlas, quiz, plagiarism). Converting it to PrimeNG DynamicDialog or signal inputs would
// silently break the `componentInstance` write contract for all callers, including carve-out files we are
// not allowed to touch. Therefore both the modal mechanism and the decorators are DEFERRED until the
// carve-out lifts and every caller can be migrated together.
export class ConfirmAutofocusModalComponent {
    modal = inject(NgbActiveModal);

    @Input() title: string;
    @Input() titleTranslationParams?: Record<string, string>;
    @Input() text: string;
    @Input() translateText: boolean;
    @Input() textIsMarkdown: boolean;
    @Input() contentRef?: TemplateRef<any>;
    @Input() confirmDisabled = false;
}
