import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';

let nextPanelId = 0;

/**
 * Owned titled content panel, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-panel`: same bordered container (content background, content border,
 * 6px radius) with a title header and an optional collapse toggle, reproduced from the Aura `panel` tokens +
 * base style. When `toggleable` is set, the header renders a real `<button>` with `aria-expanded` and
 * `aria-controls` wiring the collapsible region, and `collapsed` is a two-way `model` so callers can bind
 * `[collapsed]` (as the Iris dashboard does) or `[(collapsed)]`. The collapse uses the Aura grid-rows
 * animation. Body content projects via the default slot; an optional `[tumUiPanelFooter]` slot mirrors
 * p-card/p-panel footers.
 */
@Component({
    selector: 'tum-ui-panel',
    templateUrl: './tum-ui-panel.component.html',
    styleUrl: './tum-ui-panel.component.scss',
    imports: [FaIconComponent],
    // Panel surface = Aura content.background/border (surface.0/border light, surface.900/border dark via the
    // Artemis theme override). `block` + radius + internal paddings live in the stylesheet.
    host: {
        class: 'tum-ui-panel border border-surface rounded-md bg-surface-0 dark:bg-surface-900 text-color',
        '[attr.data-collapsed]': 'toggleable() && collapsed()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPanelComponent {
    /** Header title text (parity with p-panel `[header]`). */
    readonly header = input<string>('');
    /** Whether the panel can be collapsed via the header toggle (parity with p-panel `[toggleable]`). */
    readonly toggleable = input(false);
    /** Collapsed state; two-way so `[collapsed]` and `[(collapsed)]` both work (parity with p-panel `[(collapsed)]`). */
    readonly collapsed = model(false);

    /** Stable id linking the toggle's `aria-controls` to the collapsible region. */
    protected readonly contentId = `tum-ui-panel-content-${nextPanelId++}`;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;

    protected toggle(): void {
        if (this.toggleable()) {
            this.collapsed.update((collapsed) => !collapsed);
        }
    }
}
