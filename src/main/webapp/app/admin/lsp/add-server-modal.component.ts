import { Component } from '@angular/core';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-modal-add-lsp-server',
    template: `
        <div class="modal-header">
            <h4 class="modal-title">{{ title | artemisTranslate }}</h4>
            <button type="button" class="btn-close" aria-label="Close button" aria-describedby="modal-title" (click)="onClose()"></button>
        </div>
        <div class="modal-body">
            <div class="d-flex justify-content-center p-3 text-center">
                <span jhiTranslate="artemisApp.lsp.modals.addServerNote"></span>
            </div>
            <span>{{ text | artemisTranslate }}</span>
            <input ngbAutofocus class="form-control" type="text" required [(ngModel)]="serverUrl" />
        </div>
        <div class="modal-footer">
            <button type="button" class="btn btn-info" [disabled]="!serverUrl" (click)="onSubmit()">Ok</button>
        </div>
    `,
})
export class AddServerModalComponent {
    title: string;
    text: string;
    serverUrl: string;

    constructor(public modal: NgbActiveModal) {}

    onClose(): void {
        this.modal.close();
    }

    onSubmit(): void {
        this.modal.close(this.serverUrl);
    }
}
