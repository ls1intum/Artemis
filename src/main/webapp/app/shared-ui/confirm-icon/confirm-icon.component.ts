import { Component, input, output, signal } from '@angular/core';
import { IconProp, SizeProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
    styleUrl: './confirm-icon.component.scss',
    imports: [FaIconComponent, TumUiTooltipDirective],
})
export class ConfirmIconComponent {
    initialIcon = input<IconProp>(faTrash);
    initialTooltip = input<string>();
    confirmIcon = input<IconProp>(faCheck);
    confirmTooltip = input<string>();
    iconSize = input<SizeProp>('1x');
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
