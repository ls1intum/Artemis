import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

/**
 * Owned inline message / alert, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-message`: same grid/outline box, padding, and typography
 * (reproduced from the Aura `message` tokens + base style). Colors follow the kit's house style —
 * the four Artemis semantic state tokens (`--info`, `--success`, `--warning`, `--danger`) plus the
 * surface ramp for secondary/contrast — so a message sits next to a `tum-ui-tag` of the same
 * severity with a matching hue, dark-mode-correct for free. No icon is rendered unless `icon` is set
 * (parity with `p-message`, which only shows an icon when one is provided).
 */
export type TumUiMessageSeverity = 'info' | 'success' | 'warn' | 'error' | 'secondary' | 'contrast';

const MESSAGE_BASE = 'tum-ui-message';

// Secondary/contrast ride the surface ramp (Tailwind tokens, dark-mode via the `dark:` variant), matching
// tum-ui-tag. The four state severities are colored in the stylesheet by `data-severity` (color-mix over the
// semantic state vars), so they stay empty here.
const MESSAGE_SEVERITY: Record<TumUiMessageSeverity, string> = {
    info: '',
    success: '',
    warn: '',
    error: '',
    secondary: 'bg-surface-100 text-surface-600 outline-surface-200 dark:bg-surface-800 dark:text-surface-300 dark:outline-surface-700',
    contrast: 'bg-surface-900 text-surface-50 outline-surface-950 dark:bg-surface-0 dark:text-surface-950 dark:outline-surface-100',
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

    protected readonly hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]}`);
}
