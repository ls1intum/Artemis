import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, tumUiButtonClasses } from 'app/shared-ui/tum-ui/button/tum-ui-button.variants';

/**
 * Owned Artemis button, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Signal-based element component that renders a native `<button>` styled with Artemis token
 * utilities to match the current PrimeNG Aura look. No PrimeNG / Bootstrap / Angular CDK dependency.
 * The API intentionally mirrors TumApply's button atom (severity / size / outlined) so the two apps
 * can converge on one shared component later.
 */
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
    /** Fill style: `'solid'` (default), `'outlined'`, or `'text'`. */
    readonly variant = input<TumUiButtonVariant>('solid');
    readonly disabled = input(false);
    readonly icon = input<IconProp | undefined>(undefined);
    readonly type = input<'button' | 'submit'>('button');
    // Accessibility attributes forwarded to the inner native <button>. Placed on the <tum-ui-button> host
    // they would sit on the wrong element and leave the actual button unlabelled for assistive tech, so the
    // kit exposes the commonly-needed ones explicitly. `ariaLabel` is required for icon-only buttons (the
    // visible glyph carries no text).
    readonly ariaLabel = input<string | undefined>(undefined);
    readonly ariaExpanded = input<boolean | undefined>(undefined);
    readonly ariaPressed = input<boolean | undefined>(undefined);
    readonly ariaControls = input<string | undefined>(undefined);
    readonly ariaDescribedBy = input<string | undefined>(undefined);

    readonly clicked = output<MouseEvent>();

    protected readonly buttonClasses = computed(() => tumUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() }));

    protected onClick(event: MouseEvent): void {
        this.clicked.emit(event);
    }
}
