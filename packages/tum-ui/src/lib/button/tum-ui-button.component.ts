import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, tumUiButtonClasses } from './tum-ui-button.variants';

@Component({
    selector: 'tum-ui-button',
    templateUrl: './tum-ui-button.component.html',
    styleUrl: './tum-ui-button.component.scss',
    imports: [FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonComponent {
    readonly severity = input<TumUiButtonSeverity>('primary');
    readonly size = input<TumUiButtonSize>('default');

    readonly variant = input<TumUiButtonVariant>('solid');
    readonly disabled = input(false);

    readonly rounded = input(false);

    readonly loading = input(false);
    readonly icon = input<IconProp | undefined>(undefined);
    readonly type = input<'button' | 'submit'>('button');
    readonly ariaLabel = input<string | undefined>(undefined);
    readonly ariaExpanded = input<boolean | undefined>(undefined);
    readonly ariaPressed = input<boolean | undefined>(undefined);
    readonly ariaControls = input<string | undefined>(undefined);
    readonly ariaDescribedBy = input<string | undefined>(undefined);

    readonly styleClass = input<string>('');

    readonly clicked = output<MouseEvent>();

    protected readonly faSpinner = faSpinner;
    protected readonly isDisabled = computed(() => this.disabled() || this.loading());

    protected readonly buttonClasses = computed(() => {
        const rounded = this.rounded() ? 'tum-ui-btn-rounded' : '';
        return `${tumUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() })} ${rounded} ${this.styleClass()}`.replace(/\s+/g, ' ').trim();
    });

    protected onClick(event: MouseEvent): void {
        this.clicked.emit(event);
    }
}
