import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, tumUiButtonClasses } from './tum-ui-button.variants';
import { TumUiSeverityAlias, TumUiSizeAlias, resolveSeverity, resolveSize } from '../foundation/tum-ui-vocabulary';

@Component({
    selector: 'tum-ui-button',
    host: { '[attr.data-slot]': '"button"' },
    templateUrl: './tum-ui-button.component.html',
    styleUrl: './tum-ui-button.component.scss',
    imports: [FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonComponent {
    /** Colour role. `warn` is accepted as a deprecated spelling of `warning`, `error` of `danger`. */
    readonly severity = input<TumUiButtonSeverity | TumUiSeverityAlias>('primary');
    /** Size step. `default` and `normal` are accepted as deprecated spellings of `medium`. */
    readonly size = input<TumUiButtonSize | TumUiSizeAlias>('medium');

    readonly variant = input<TumUiButtonVariant>('solid');
    readonly disabled = input(false, { transform: booleanAttribute });

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

    protected readonly effectiveSeverity = computed(() => resolveSeverity<TumUiButtonSeverity>(this.severity(), 'tum-ui-button'));
    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tum-ui-button'));

    protected readonly buttonClasses = computed(() => {
        const rounded = this.rounded() ? 'tum-ui-btn-rounded' : '';
        return `${tumUiButtonClasses({ severity: this.effectiveSeverity(), size: this.effectiveSize(), variant: this.variant() })} ${rounded}`.trim();
    });

    protected onClick(event: MouseEvent): void {
        this.clicked.emit(event);
    }
}
