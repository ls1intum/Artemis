import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
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

    readonly removable = input(false);

    readonly size = input<TumUiChipSize>();

    readonly removeAriaLabel = input<string>();

    readonly styleClass = input<string>('');

    readonly onRemove = output<Event>();

    protected readonly faXmark = faXmark;

    protected readonly chipClasses = computed(() => {
        const small = this.size() === 'small';
        const type = small ? 'tum:gap-1 tum:text-sm' : 'tum:gap-2 tum:text-base';
        const padding = small ? (this.removable() ? 'tum:py-1 tum:pl-2 tum:pr-1' : 'tum:px-2 tum:py-1') : this.removable() ? 'tum:py-2 tum:pl-3 tum:pr-2' : 'tum:px-3 tum:py-2';
        const base =
            'tum:inline-flex tum:items-center tum:rounded-2xl tum:bg-tum-ui-surface-100 tum:text-tum-ui-surface-800 tum:dark:bg-tum-ui-surface-800 tum:dark:text-tum-ui-surface-0';
        return `${base} ${type} ${padding} ${this.styleClass()}`.trim();
    });

    protected remove(event: Event): void {
        this.onRemove.emit(event);
    }
    protected onRemoveKeydown(event: KeyboardEvent): void {
        if (event.key === 'Backspace' || event.key === 'Delete') {
            event.preventDefault();
            this.remove(event);
        }
    }
}
