import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

export type TumAetUiMessageSeverity = 'info' | 'success' | 'warn' | 'error' | 'secondary' | 'contrast';

const MESSAGE_BASE = 'tumaet-ui-message';

const MESSAGE_SEVERITY: Record<TumAetUiMessageSeverity, string> = {
    info: '',
    success: '',
    warn: '',
    error: '',
    secondary: 'tumaet:bg-hover-background tumaet:text-text tumaet:outline-border',
    contrast: 'tumaet:bg-contrast-background tumaet:text-contrast tumaet:outline-contrast-background',
};

@Component({
    selector: 'tumaet-ui-message',
    templateUrl: './tumaet-ui-message.component.html',
    styleUrl: './tumaet-ui-message.component.scss',
    imports: [FaIconComponent],
    host: {
        '[attr.role]': 'messageRole()',
        '[class]': 'hostClasses()',
        '[attr.data-severity]': 'severity()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiMessageComponent {
    readonly severity = input<TumAetUiMessageSeverity>('info');

    readonly text = input<string>();

    readonly icon = input<IconProp>();

    protected readonly messageRole = computed(() => (this.severity() === 'error' ? 'alert' : 'status'));

    protected readonly hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]}`.trim());
}
