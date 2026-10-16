import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiTranslatePipe } from '../i18n/tumaet-ui-translate.pipe';

export type TumAetUiChipSize = 'small';

@Component({
    selector: 'tumaet-ui-chip',
    templateUrl: './tumaet-ui-chip.component.html',
    styleUrl: './tumaet-ui-chip.component.scss',
    imports: [FaIconComponent, TumAetUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiChipComponent {
    readonly label = input<string>();

    readonly removable = input(false, { transform: booleanAttribute });

    readonly size = input<TumAetUiChipSize>();

    readonly removeAriaLabel = input<string>();

    readonly removed = output<Event>();

    protected readonly faXmark = faXmark;

    protected readonly chipClasses = computed(() => {
        const small = this.size() === 'small';
        const type = small ? 'tumaet:gap-1 tumaet:text-sm' : 'tumaet:gap-2 tumaet:text-base';
        const padding = small
            ? this.removable()
                ? 'tumaet:py-1 tumaet:ps-2 tumaet:pe-1'
                : 'tumaet:px-2 tumaet:py-1'
            : this.removable()
              ? 'tumaet:py-2 tumaet:ps-3 tumaet:pe-2'
              : 'tumaet:px-3 tumaet:py-2';
        const base = 'tumaet:inline-flex tumaet:items-center tumaet:rounded-2xl tumaet:bg-hover-background tumaet:text-text';
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
