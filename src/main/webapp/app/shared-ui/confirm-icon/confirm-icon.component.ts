import { Component, computed, input, output, signal } from '@angular/core';
import { IconProp, SizeProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faTrash } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-confirm-icon',
    templateUrl: './confirm-icon.component.html',
    styleUrl: './confirm-icon.component.scss',
    imports: [FaIconComponent, TumAetUiTooltipDirective],
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
     * a single button element is kept across both states so keyboard focus (and thus the second
     * Enter/Space of the two-step confirmation) is never lost when showConfirm toggles
     */
    protected readonly activeIcon = computed(() => (this.showConfirm() ? this.confirmIcon() : this.initialIcon()));
    protected readonly activeTooltip = computed(() => (this.showConfirm() ? this.confirmTooltip() : this.initialTooltip()) ?? '');

    /**
     * confirm the action if already in confirm state, otherwise arm it
     */
    handleActivate(): void {
        if (this.showConfirm()) {
            this.confirmAction();
        } else {
            this.toggle();
        }
    }

    /**
     * revert to the initial state, e.g. when the pointer leaves or focus moves away
     */
    revertIfConfirming(): void {
        if (this.showConfirm()) {
            this.toggle();
        }
    }

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
