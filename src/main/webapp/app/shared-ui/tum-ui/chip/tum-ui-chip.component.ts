import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';

/**
 * Owned label / token pill, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-chip`: a rounded pill with a text `[label]` (or projected content)
 * and an optional keyboard-accessible remove control. Styled from the exact Aura `chip` tokens (surface-100
 * background / surface-800 label in light, surface-800 / surface-0 in dark; 16px radius; the `paddingX 0.75rem`
 * / `paddingY 0.5rem` / `gap 0.5rem` metrics) so it renders like the widget it replaces, dark-mode-correct for
 * free via the `dark:` token variants. No PrimeNG / Bootstrap dependency.
 *
 * `[removable]` reveals an accessible `×` button (a real, labeled `<button>`): mouse click, Enter / Space (native
 * button activation), and Backspace / Delete (while the button is focused) all emit {@link onRemove}. The chip is
 * presentational — the parent owns the list and drops the item in the handler, exactly like `p-chip (onRemove)`.
 * The `small` size renders the compact chip used inside {@link TumUiAutoCompleteComponent}'s multi-select input.
 */
export type TumUiChipSize = 'small';

@Component({
    selector: 'tum-ui-chip',
    templateUrl: './tum-ui-chip.component.html',
    styleUrl: './tum-ui-chip.component.scss',
    imports: [FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiChipComponent {
    /** Text shown on the pill. Omit to project custom content via `<ng-content>` (parity with `p-chip`). */
    readonly label = input<string>();
    /** Renders the accessible remove (`×`) button (parity with `p-chip [removable]`). */
    readonly removable = input(false);
    /** `small` renders the compact in-field chip (used by the autocomplete); omit for the default size. */
    readonly size = input<TumUiChipSize>();
    /** Accessible name for the remove button; overridable for i18n. */
    readonly removeAriaLabel = input<string>('Remove');
    /** Extra classes forwarded onto the pill (drop-in for `p-chip styleClass`). */
    readonly styleClass = input<string>('');

    /** Emitted when the remove control is activated (mouse / Enter / Space / Backspace). Parity with `p-chip (onRemove)`. */
    readonly onRemove = output<Event>();

    protected readonly faXmark = faXmark;

    protected readonly chipClasses = computed(() => {
        const small = this.size() === 'small';
        const type = small ? 'gap-1 text-sm' : 'gap-2 text-base';
        // Aura `chip:has(.p-chip-remove-icon)` shrinks the trailing padding to `paddingY`; keep the leading padding.
        const padding = small ? (this.removable() ? 'py-1 pl-2 pr-1' : 'px-2 py-1') : this.removable() ? 'py-2 pl-3 pr-2' : 'px-3 py-2';
        const base = 'inline-flex items-center rounded-2xl bg-surface-100 text-surface-800 dark:bg-surface-800 dark:text-surface-0';
        return `${base} ${type} ${padding} ${this.styleClass()}`.trim();
    });

    protected remove(event: Event): void {
        this.onRemove.emit(event);
    }

    /** Backspace / Delete while the remove button is focused removes the chip (Enter / Space go through the native click). */
    protected onRemoveKeydown(event: KeyboardEvent): void {
        if (event.key === 'Backspace' || event.key === 'Delete') {
            event.preventDefault();
            this.remove(event);
        }
    }
}
