import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IconProp, SizeProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
    imports: [FaIconComponent, NgbTooltip, NgClass],
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
