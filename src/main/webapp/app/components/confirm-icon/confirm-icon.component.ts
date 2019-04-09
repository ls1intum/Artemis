import { Component, Output, EventEmitter, Input, OnInit } from '@angular/core';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
})
export class ConfirmIconComponent implements OnInit {
    @Input() initialIcon: string = 'trash';
    @Input() initialTooltip: string;
    @Input() confirmIcon: string = 'check';
    @Input() confirmTooltip: string;
    @Output() confirmEvent = new EventEmitter();
    showConfirm: boolean = false;

    ngOnInit(): void {}

    confirmAction() {
        this.showConfirm = !this.showConfirm;
        this.confirmEvent.emit(true);
    }
}
