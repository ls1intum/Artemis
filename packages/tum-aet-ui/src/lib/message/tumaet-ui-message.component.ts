import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiTranslatePipe } from '../i18n/tumaet-ui-translate.pipe';

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
    imports: [FaIconComponent, TumAetUiTranslatePipe],
    host: {
        '[attr.role]': 'messageRole()',
        '[class]': 'hostClasses()',
        '[attr.data-severity]': 'severity()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiMessageComponent {
    readonly severity = input<TumAetUiMessageSeverity>('info');

    /** Disable when a surrounding live announcer already reports this message. */
    readonly announce = input(true, { transform: booleanAttribute });

    readonly text = input<string>();

    readonly icon = input<IconProp>();

    /** Shows a dismiss button at the end of the message. The message does not hide itself: the host removes it on {@link dismissed}. */
    readonly dismissible = input(false, { transform: booleanAttribute });

    /** Accessible name of the dismiss button; defaults to the translated "Dismiss". */
    readonly dismissButtonAriaLabel = input<string>();

    readonly dismissed = output<void>();

    protected readonly faXmark = faXmark;

    protected readonly messageRole = computed(() => (this.announce() ? (this.severity() === 'error' ? 'alert' : 'status') : null));

    protected readonly hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]}`.trim());
}
