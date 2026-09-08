import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, tumUiButtonClasses } from './tum-ui-button.variants';
import { TumUiSeverityAlias, TumUiSizeAlias, resolveSeverity, resolveSize } from '../foundation/tum-ui-vocabulary';

@Component({
    selector: 'a[tumUiButton], button[tumUiButton]',
    template: '<ng-content />',
    styleUrl: './tum-ui-button.directive.scss',
    host: {
        '[attr.data-slot]': '"button"',
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonDirective {
    /** Colour role. `warn` is accepted as a deprecated spelling of `warning`, `error` of `danger`. */
    readonly severity = input<TumUiButtonSeverity | TumUiSeverityAlias>('primary');
    /** Size step. `default` and `normal` are accepted as deprecated spellings of `medium`. */
    readonly size = input<TumUiButtonSize | TumUiSizeAlias>('medium');

    readonly variant = input<TumUiButtonVariant>('solid');

    protected readonly effectiveSeverity = computed(() => resolveSeverity<TumUiButtonSeverity>(this.severity(), 'tumUiButton'));
    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tumUiButton'));

    protected readonly hostClasses = computed(() => tumUiButtonClasses({ severity: this.effectiveSeverity(), size: this.effectiveSize(), variant: this.variant() }));
}
