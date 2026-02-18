import { Component, input, output, signal } from '@angular/core';
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
    initialIcon = input<IconProp>(faTrash);
    initialTooltip = input<string>();
    confirmIcon = input<IconProp>(faCheck);
    confirmTooltip = input<string>();
    iconSize = input<SizeProp>('md');
    confirmEvent = output<boolean>();
    showConfirm = signal(false);

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
        this.showConfirm.update((confirmToggled) => !confirmToggled);
    }
}
