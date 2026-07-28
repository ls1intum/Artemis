import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

export type TumUiMessageSeverity = 'info' | 'success' | 'warn' | 'error' | 'secondary' | 'contrast';

const MESSAGE_BASE = 'tum-ui-message';

const MESSAGE_SEVERITY: Record<TumUiMessageSeverity, string> = {
    info: '',
    success: '',
    warn: '',
    error: '',
    secondary:
        'tum:bg-tum-ui-surface-100 tum:text-tum-ui-surface-600 tum:outline-tum-ui-surface-200 tum:dark:bg-tum-ui-surface-800 tum:dark:text-tum-ui-surface-300 tum:dark:outline-tum-ui-surface-700',
    contrast:
        'tum:bg-tum-ui-surface-900 tum:text-tum-ui-surface-50 tum:outline-tum-ui-surface-950 tum:dark:bg-tum-ui-surface-0 tum:dark:text-tum-ui-surface-950 tum:dark:outline-tum-ui-surface-100',
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

    readonly text = input<string>();

    readonly icon = input<IconProp>();

    readonly styleClass = input<string>('');

    protected readonly hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]} ${this.styleClass()}`.trim());
}
