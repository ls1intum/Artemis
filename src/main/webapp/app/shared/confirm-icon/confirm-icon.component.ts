import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
})
export class ConfirmIconComponent implements OnInit {
    @Input() initialIcon = <IconProp>'trash';
    @Input() initialTooltip: string;
    @Input() confirmIcon = <IconProp>'check';
    @Input() confirmTooltip: string;
    @Output() confirmEvent = new EventEmitter();
    showConfirm = false;

    /**
     * do nothing
     */
    ngOnInit(): void {}

    /**
     * call toggle and emit confirmEvent
     */
    confirmAction(): void {
        this.toggle();
        this.confirmEvent.emit(true);
    }

    /**
     * toggle showConfirm
     */
    toggle(): void {
        this.showConfirm = !this.showConfirm;
    }
}
