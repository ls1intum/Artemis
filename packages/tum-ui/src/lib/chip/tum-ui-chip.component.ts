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
        const type = small ? 'gap-1 text-sm' : 'gap-2 text-base';
        const padding = small ? (this.removable() ? 'py-1 pl-2 pr-1' : 'px-2 py-1') : this.removable() ? 'py-2 pl-3 pr-2' : 'px-3 py-2';
        const base = 'inline-flex items-center rounded-2xl bg-tum-ui-surface-100 text-tum-ui-surface-800 dark:bg-tum-ui-surface-800 dark:text-tum-ui-surface-0';
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
