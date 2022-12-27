import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IconProp, SizeProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faTrash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
})
export class ConfirmIconComponent {
    @Input() initialIcon = <IconProp>faTrash;
    @Input() initialTooltip: string;
    @Input() confirmIcon = <IconProp>faCheck;
    @Input() confirmTooltip: string;
    @Input() iconSize = <SizeProp>'md';
    @Output() confirmEvent = new EventEmitter();
    showConfirm = false;

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
