import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

@Component({
    template: `
        <div class="modal-header">
            <h4 class="modal-title">{{ title | translate }}</h4>
            <button type="button" class="close" aria-label="Close button" aria-describedby="modal-title" (click)="modal.dismiss('Cross click')">
                <span aria-hidden="true">&times;</span>
            </button>
        </div>
        <div class="modal-body">
            <p>{{ text | translate }}</p>
        </div>
        <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" (click)="modal.dismiss('cancel click')" jhiTranslate="global.form.cancel">Cancel</button>
            <button type="button" ngbAutofocus class="btn btn-danger" (click)="modal.close('Ok click')" jhiTranslate="global.form.confirm">Confirm</button>
        </div>
    `,
})
export class ConfirmAutofocusModalComponent {
    title: string;
    text: string;

    constructor(public modal: NgbActiveModal) {}
}

@Component({
    selector: 'jhi-confirm-button',
    template: ` <jhi-button [icon]="icon" [title]="title" [tooltip]="tooltip" [disabled]="disabled" [isLoading]="isLoading" (onClick)="onOpenConfirmationModal()"></jhi-button> `,
})
export class ConfirmAutofocusButtonComponent {
    @Input() icon: string;
    @Input() title: string;
    @Input() tooltip: string;
    @Input() disabled = false;
    @Input() isLoading = false;

    @Input() confirmationTitle: string;
    @Input() confirmationText: string;
    @Output() onConfirm = new EventEmitter<void>();
    @Output() onCancel = new EventEmitter<void>();

    constructor(private modalService: NgbModal) {}

    onOpenConfirmationModal() {
        const modalRef: NgbModalRef = this.modalService.open(ConfirmAutofocusModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.text = this.confirmationText;
        modalRef.componentInstance.title = this.confirmationTitle;
        modalRef.result.then(
            (result) => {
                this.onConfirm.emit();
            },
            (reason) => {
                this.onCancel.emit();
            },
        );
    }
}
