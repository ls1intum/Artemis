import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, SimpleChanges, inject } from '@angular/core';
import { round } from 'app/shared/util/utils';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { Subscription } from 'rxjs';
import { toObservable } from '@angular/core/rxjs-interop';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-progress-bar',
    templateUrl: './progress-bar.component.html',
    imports: [NgbTooltip, NgClass],
})
export class ProgressBarComponent implements OnChanges, OnDestroy {
    private themeService = inject(ThemeService);
    private ref = inject(ChangeDetectorRef);

    @Input() public tooltip: string;
    @Input() public percentage: number;
    @Input() public numerator: number;
    @Input() public denominator: number;

    foregroundColorClass: string;
    backgroundColorClass: string;
    themeSubscription: Subscription;

    constructor() {
        this.themeSubscription = toObservable(this.themeService.currentTheme).subscribe(() => {
            this.chooseProgressBarTextColor();

            // Manually run change detection as it doesn't do it automatically for some reason
            this.ref.detectChanges();
        });
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.percentage) {
            this.percentage = round(this.percentage);
            this.chooseProgressBarTextColor();
            this.calculateProgressBarClass();
        }
    }

    ngOnDestroy() {
        this.themeSubscription.unsubscribe();
    }

    /**
     * Function to render the correct progress bar class
     */
    calculateProgressBarClass(): void {
        if (this.percentage < 50) {
            this.backgroundColorClass = 'bg-danger';
        } else if (this.percentage < 100) {
            this.backgroundColorClass = 'bg-warning';
        } else {
            this.backgroundColorClass = 'bg-success';
        }
    }

    /**
     * Function to change the text color to indicate a finished status
     */
    chooseProgressBarTextColor() {
        switch (this.themeService.currentTheme()) {
            case Theme.DARK:
                this.foregroundColorClass = 'text-white';
                break;
            case Theme.LIGHT:
            default:
                if (this.percentage < 100) {
                    this.foregroundColorClass = 'text-dark';
                } else {
                    this.foregroundColorClass = 'text-white';
                }
        }
    }
}
