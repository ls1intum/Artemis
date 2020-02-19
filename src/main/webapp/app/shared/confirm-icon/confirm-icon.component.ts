import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
})
export class ConfirmIconComponent implements OnInit {
    @Input() initialIcon = 'trash';
    @Input() initialTooltip: string;
    @Input() confirmIcon = 'check';
    @Input() confirmTooltip: string;
    @Output() confirmEvent = new EventEmitter();
    showConfirm = false;

    ngOnInit(): void {}

    confirmAction() {
        this.showConfirm = !this.showConfirm;
        this.confirmEvent.emit(true);
    }
}
