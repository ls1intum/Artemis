import { TumAetUiProgressBarComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { round } from 'app/foundation/util/utils';

@Component({
    selector: 'jhi-progress-bar',
    templateUrl: './progress-bar.component.html',
    imports: [TumAetUiProgressBarComponent, TumAetUiTooltipDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressBarComponent {
    readonly tooltip = input<string>('');
    readonly percentage = input<number>(0);
    readonly numerator = input<number>(0);
    readonly denominator = input<number>(0);

    /** Sanitized percentage: 0 if not a number, or if both numerator and denominator are zero. */
    readonly normalizedPercentage = computed(() => {
        const isInvalid = this.numerator() === 0 && this.denominator() === 0;
        const value = this.percentage();
        if (isNaN(value) || isInvalid) {
            return 0;
        }
        return round(value);
    });
}
