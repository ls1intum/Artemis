import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostBinding, Input, OnInit, inject } from '@angular/core';

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
})
export class VerticalProgressBarComponent implements OnInit {
    private cdr = inject(ChangeDetectorRef);

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

    ngOnInit(): void {
        this.setFillColor();
    }

    fillLevelInPercentInternal = 0;
    lowerBorderInternal = 60;
    upperBorderInternal = 90;

    @Input()
    set lowerBorder(border: number) {
        this.lowerBorderInternal = Math.round(Math.max(border, 0));
        this.setFillColor();
        this.cdr.markForCheck();
    }

    @Input()
    set upperBorder(border: number) {
        this.upperBorderInternal = Math.round(Math.min(border, 100));
        this.setFillColor();
        this.cdr.markForCheck();
    }
    @Input()
    lowerColor = 'var(--success)';
    @Input()
    intermediateColor = 'var(--warning)';
    @Input()
    upperColor = 'var(--danger)';
    @Input()
    tooltip = '';

    @Input()
    set animateFilling(showAnimation: boolean) {
        if (showAnimation) {
            this.fillDurationCSS = '1s';
        } else {
            this.fillDurationCSS = '0s';
        }
    }

    @Input()
    set heightInPixels(height: number) {
        if (height !== undefined && height !== null && height > 0) {
            this.heightCSS = `${height}px`;
            this.cdr.markForCheck();
        }
    }

    @Input()
    set widthInPixels(width: number) {
        if (width !== undefined && width !== null && width > 0) {
            this.widthCSS = `${width}px`;
            this.cdr.markForCheck();
        }
    }

    @Input()
    set fillLevelInPercent(percent: number) {
        if (percent !== undefined && percent !== null) {
            this.fillLevelInPercentInternal = Math.min(Math.max(Math.round(percent), 0), 100);
            this.fillLevelCSS = `${this.fillLevelInPercentInternal}%`;
            this.setFillColor();
            this.cdr.markForCheck();
        }
    }

    private setFillColor() {
        if (this.fillLevelInPercentInternal <= this.lowerBorderInternal) {
            this.fillColorCSS = this.lowerColor;
        } else if (this.fillLevelInPercentInternal >= this.upperBorderInternal) {
            this.fillColorCSS = this.upperColor;
        } else {
            this.fillColorCSS = this.intermediateColor;
        }
    }
}
