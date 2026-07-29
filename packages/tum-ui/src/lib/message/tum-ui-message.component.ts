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
    secondary: 'tum:bg-tum-ui-hover-background tum:text-tum-ui-text tum:outline-tum-ui-border',
    contrast: 'tum:bg-tum-ui-contrast-background tum:text-tum-ui-contrast tum:outline-tum-ui-contrast-background',
};

@Component({
    selector: 'tum-ui-message',
    templateUrl: './tum-ui-message.component.html',
    styleUrl: './tum-ui-message.component.scss',
    imports: [FaIconComponent],
    host: {
        '[attr.role]': 'messageRole()',
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

    protected readonly messageRole = computed(() => (this.severity() === 'error' ? 'alert' : 'status'));

    protected readonly hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]} ${this.styleClass()}`.trim());
}
