import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiDisabledReasonDirective } from '../disabled-reason/tumaet-ui-disabled-reason.directive';
import { TumAetUiButtonSeverity, TumAetUiButtonSize, TumAetUiButtonVariant, tumAetUiButtonClasses } from './tumaet-ui-button.variants';

@Component({
    selector: 'tumaet-ui-button',
    templateUrl: './tumaet-ui-button.component.html',
    styleUrl: './tumaet-ui-button.component.scss',
    imports: [FaIconComponent, TumAetUiDisabledReasonDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiButtonComponent {
    readonly severity = input<TumAetUiButtonSeverity>('primary');
    readonly size = input<TumAetUiButtonSize>('default');

    readonly variant = input<TumAetUiButtonVariant>('solid');
    readonly disabled = input(false, { transform: booleanAttribute });
    /** Keep the button focusable and explain why it cannot be used. */
    readonly disabledReason = input<string | undefined>(undefined);

    readonly rounded = input(false, { transform: booleanAttribute });

    /** Replaces the icon with a spinner and disables the button. */
    readonly loading = input(false, { transform: booleanAttribute });
    readonly icon = input<IconProp | undefined>(undefined);
    readonly type = input<'button' | 'submit'>('button');
    /** Accessible name required when projected content does not label the button. */
    readonly ariaLabel = input<string | undefined>(undefined);
    readonly ariaExpanded = input<boolean | undefined>(undefined);
    readonly ariaPressed = input<boolean | undefined>(undefined);
    readonly ariaControls = input<string | undefined>(undefined);
    readonly ariaDescribedBy = input<string | undefined>(undefined);

    readonly clicked = output<MouseEvent>();

    protected readonly faSpinner = faSpinner;
    protected readonly isDisabled = computed(() => this.disabled() || this.loading());

    protected readonly buttonClasses = computed(() => {
        const rounded = this.rounded() ? 'tumaet-ui-btn-rounded' : '';
        return `${tumAetUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() })} ${rounded}`.trim();
    });

    protected onClick(event: MouseEvent): void {
        this.clicked.emit(event);
    }
}
