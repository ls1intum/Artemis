import { Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-modal-confirm-autofocus',
    templateUrl: './modal-confirm-autofocus.component.html',
    imports: [ArtemisTranslatePipe],
})
export class ModalConfirmAutofocusComponent {
    modal = inject(NgbActiveModal);

    title: string;
    text: string;
}
