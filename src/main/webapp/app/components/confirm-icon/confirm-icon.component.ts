import { Component, Output, EventEmitter, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
})
export class ConfirmIconComponent implements OnInit {
    @Input() initialIcon: string;
    @Input() initialTooltip: string;
    @Input() confirmIcon: string;
    @Input() confirmTooltip: string;
    @Output() confirmEvent = new EventEmitter();
    showConfirm = false;

    ngOnInit(): void {}

    confirmAction() {
        this.showConfirm = !this.showConfirm;
        this.confirmEvent.emit(true);
    }
}
