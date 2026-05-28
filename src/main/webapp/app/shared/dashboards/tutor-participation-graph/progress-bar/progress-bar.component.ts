import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { round } from 'app/shared/util/utils';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { NgClass } from '@angular/common';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-progress-bar',
    templateUrl: './progress-bar.component.html',
    imports: [NgClass, TooltipModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressBarComponent {
    private themeService = inject(ThemeService);

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

    readonly backgroundColorClass = computed(() => {
        const value = this.normalizedPercentage();
        if (value < 50) {
            return 'bg-danger';
        }
        if (value < 100) {
            return 'bg-warning';
        }
        return 'bg-success';
    });

    readonly foregroundColorClass = computed(() => {
        if (this.themeService.currentTheme() === Theme.DARK) {
            return 'text-white';
        }
        return this.normalizedPercentage() < 100 ? 'text-dark' : 'text-white';
    });
}
