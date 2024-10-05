import { Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-modal-confirm-autofocus',
    templateUrl: './modal-confirm-autofocus.component.html',
})
export class ModalConfirmAutofocusComponent {
    modal = inject(NgbActiveModal);

    title: string;
    text: string;
}
