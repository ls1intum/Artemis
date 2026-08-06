import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

export type TumUiChipSize = 'small';

@Component({
    selector: 'tum-ui-chip',
    templateUrl: './tum-ui-chip.component.html',
    styleUrl: './tum-ui-chip.component.scss',
    imports: [FaIconComponent, TumUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiChipComponent {
    readonly label = input<string>();

    readonly removable = input(false, { transform: booleanAttribute });

    readonly size = input<TumUiChipSize>();

    readonly removeAriaLabel = input<string>();

    readonly removed = output<Event>();

    protected readonly faXmark = faXmark;

    protected readonly chipClasses = computed(() => {
        const small = this.size() === 'small';
        const type = small ? 'tum:gap-1 tum:text-sm' : 'tum:gap-2 tum:text-base';
        const padding = small ? (this.removable() ? 'tum:py-1 tum:ps-2 tum:pe-1' : 'tum:px-2 tum:py-1') : this.removable() ? 'tum:py-2 tum:ps-3 tum:pe-2' : 'tum:px-3 tum:py-2';
        const base = 'tum:inline-flex tum:items-center tum:rounded-2xl tum:bg-hover-background tum:text-text';
        return `${base} ${type} ${padding}`;
    });

    protected remove(event: Event): void {
        this.removed.emit(event);
    }
    protected onRemoveKeydown(event: KeyboardEvent): void {
        if (event.key === 'Backspace' || event.key === 'Delete') {
            event.preventDefault();
            this.remove(event);
        }
    }
}
