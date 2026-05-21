import { ChangeDetectionStrategy, Component, HostBinding, computed, effect, input } from '@angular/core';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

/**
 * Simple Vertical Progress Bar without any external dependencies
 * <ul>
 *     <li> Supports three different colors depending on fill level
 *     <li> [upperBorder, 100] -> upper color
 *     <li> (lowerBorder, upperBorder) -> intermediate color
 *     <li> [0,lowerBorder] -> lower color
 * </ul>
 */
@Component({
    selector: 'jhi-vertical-progress-bar',
    templateUrl: './vertical-progress-bar.component.html',
    styleUrls: ['./vertical-progress-bar.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgbTooltip],
})
export class VerticalProgressBarComponent {
    // Input signals
    readonly lowerBorder = input(60);
    readonly upperBorder = input(90);
    readonly lowerColor = input('var(--success)');
    readonly intermediateColor = input('var(--warning)');
    readonly upperColor = input('var(--danger)');
    readonly tooltip = input('');
    readonly animateFilling = input(true);
    readonly heightInPixels = input<number>();
    readonly widthInPixels = input<number>();
    readonly fillLevelInPercent = input(0);

    // Computed values for internal processing
    readonly lowerBorderInternal = computed(() => Math.round(Math.max(this.lowerBorder(), 0)));
    readonly upperBorderInternal = computed(() => Math.round(Math.min(this.upperBorder(), 100)));
    readonly fillLevelInPercentInternal = computed(() => Math.min(Math.max(Math.round(this.fillLevelInPercent()), 0), 100));

    // CSS VARIABLES START
    @HostBinding('style.--progress-bar-height')
    heightCSS = '50px';
    @HostBinding('style.--progress-bar-width')
    widthCSS = '50px';
    @HostBinding('style.--fill-level')
    fillLevelCSS = '0%';
    @HostBinding('style.--fill-color')
    fillColorCSS = 'green';
    @HostBinding('style.--fill-duration')
    fillDurationCSS = '1s';
    @HostBinding('style.--border-radius')
    borderRadiusCSS = '16px';
    // CSS VARIABLES END

    constructor() {
        // Effect to update height CSS
        effect(() => {
            const height = this.heightInPixels();
            if (height !== undefined && height !== null && height > 0) {
                this.heightCSS = `${height}px`;
            }
        });

        // Effect to update width CSS
        effect(() => {
            const width = this.widthInPixels();
            if (width !== undefined && width !== null && width > 0) {
                this.widthCSS = `${width}px`;
            }
        });

        // Effect to update animation duration CSS
        effect(() => {
            this.fillDurationCSS = this.animateFilling() ? '1s' : '0s';
        });

        // Effect to update fill level and color CSS
        effect(() => {
            const fillLevel = this.fillLevelInPercentInternal();
            const lowerBorder = this.lowerBorderInternal();
            const upperBorder = this.upperBorderInternal();

            this.fillLevelCSS = `${fillLevel}%`;

            if (fillLevel <= lowerBorder) {
                this.fillColorCSS = this.lowerColor();
            } else if (fillLevel >= upperBorder) {
                this.fillColorCSS = this.upperColor();
            } else {
                this.fillColorCSS = this.intermediateColor();
            }
        });
    }
}
