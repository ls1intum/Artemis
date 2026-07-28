import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Inline message / alert.
 *
 * Drop-in replacement for PrimeNG's `p-message`: same grid/outline box, padding, and typography
 * (reproduced from the Aura `message` tokens + base style). Colors follow the kit's house style —
 * the four Artemis semantic state tokens (`--tum-ui-state-info`, `--tum-ui-state-success`, `--tum-ui-state-warning`, `--tum-ui-state-danger`) plus the
 * surface ramp for secondary/contrast — so a message sits next to a `tum-ui-tag` of the same
 * severity with a matching hue, dark-mode-correct for free. No icon is rendered unless `icon` is set
 * (parity with `p-message`, which only shows an icon when one is provided).
 */
export type TumUiMessageSeverity = 'info' | 'success' | 'warn' | 'error' | 'secondary' | 'contrast';

const MESSAGE_BASE = 'tum-ui-message';

const MESSAGE_SEVERITY: Record<TumUiMessageSeverity, string> = {
    info: '',
    success: '',
    warn: '',
    error: '',
    secondary: 'bg-tum-ui-surface-100 text-tum-ui-surface-600 outline-tum-ui-surface-200 dark:bg-tum-ui-surface-800 dark:text-tum-ui-surface-300 dark:outline-tum-ui-surface-700',
    contrast: 'bg-tum-ui-surface-900 text-tum-ui-surface-50 outline-tum-ui-surface-950 dark:bg-tum-ui-surface-0 dark:text-tum-ui-surface-950 dark:outline-tum-ui-surface-100',
};

@Component({
    selector: 'tum-ui-message',
    templateUrl: './tum-ui-message.component.html',
    styleUrl: './tum-ui-message.component.scss',
    imports: [FaIconComponent],
    host: {
        '[class]': 'hostClasses()',
        '[attr.data-severity]': 'severity()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiMessageComponent {
    readonly severity = input<TumUiMessageSeverity>('info');
    /** Plain-text message. When set, it is rendered instead of projected content (parity with p-message `[text]`). */
    readonly text = input<string>();
    /** Optional leading FontAwesome icon; none is shown by default, exactly like p-message. */
    readonly icon = input<IconProp>();
    /** Extra classes forwarded onto the message box (drop-in for p-message `styleClass`, e.g. `mb-3 w-full`). */
    readonly styleClass = input<string>('');

    protected readonly hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]} ${this.styleClass()}`.trim());
}
