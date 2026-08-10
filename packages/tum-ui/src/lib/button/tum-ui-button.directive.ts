import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, tumUiButtonClasses } from './tum-ui-button.variants';

@Component({
    selector: 'a[tumUiButton], button[tumUiButton]',
    template: '<ng-content />',
    styleUrl: './tum-ui-button.directive.scss',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonDirective {
    readonly severity = input<TumUiButtonSeverity>('primary');
    readonly size = input<TumUiButtonSize>('default');

    readonly variant = input<TumUiButtonVariant>('solid');

    protected readonly hostClasses = computed(() => tumUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() }));
}
