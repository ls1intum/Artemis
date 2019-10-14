import { Component, EventEmitter, OnInit, Output } from '@angular/core';

// TODO this can probably be moved to one of our generic delete components
@Component({
    selector: 'jhi-confirm-delete-button',
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
