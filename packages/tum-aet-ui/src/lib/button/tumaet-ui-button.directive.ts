import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumAetUiButtonSeverity, TumAetUiButtonSize, TumAetUiButtonVariant, tumAetUiButtonClasses } from './tumaet-ui-button.variants';

@Component({
    selector: 'a[tumAetUiButton], button[tumAetUiButton]',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-button.directive.scss',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiButtonDirective {
    readonly severity = input<TumAetUiButtonSeverity>('primary');
    readonly size = input<TumAetUiButtonSize>('default');

    readonly variant = input<TumAetUiButtonVariant>('solid');

    protected readonly hostClasses = computed(() => tumAetUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() }));
}
