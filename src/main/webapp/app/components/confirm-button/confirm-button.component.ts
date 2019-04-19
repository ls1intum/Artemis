import { Component, EventEmitter, OnInit, Output } from '@angular/core';

@Component({
    selector: 'jhi-confirm-button',
    templateUrl: './confirm-button.component.html',
    styles: [
        `
            button {
                min-width: 100px;
            }
        `,
    ],
})
export class ConfirmButtonComponent implements OnInit {
    @Output() confirmEvent = new EventEmitter();
    showConfirm = false;

    ngOnInit(): void {}

    confirmAction() {
        this.showConfirm = !this.showConfirm;
        this.confirmEvent.emit(true);
    }
}
