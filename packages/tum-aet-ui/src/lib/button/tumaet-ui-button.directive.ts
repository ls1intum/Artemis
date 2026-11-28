import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
import { TumAetUiButtonSeverity, TumAetUiButtonSize, TumAetUiButtonVariant, tumAetUiButtonClasses } from './tumaet-ui-button.variants';

@Component({
    selector: 'a[tumAetUiButton], button[tumAetUiButton]',
    template: '<ng-content />',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiButtonDirective {
    readonly severity = input<TumAetUiButtonSeverity>('primary');
    readonly size = input<TumAetUiButtonSize>('default');

    readonly variant = input<TumAetUiButtonVariant>('solid');
    /** Fully rounded presentation, e.g. for icon-only native buttons and links; matches `tumaet-ui-button [rounded]`. */
    readonly rounded = input(false, { transform: booleanAttribute });

    protected readonly hostClasses = computed(() => {
        const classes = tumAetUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() });
        return this.rounded() ? `${classes} tumaet-ui-btn-rounded` : classes;
    });
}
