import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-modal-confirm-autofocus',
    templateUrl: './modal-confirm-autofocus.component.html',
})
export class ModalConfirmAutofocusComponent {
    title: string;
    text: string;

    constructor(public modal: NgbActiveModal) {}
}
