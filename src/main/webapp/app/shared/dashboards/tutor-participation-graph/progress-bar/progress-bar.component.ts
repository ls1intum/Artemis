import { ChangeDetectorRef, Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { round } from 'app/shared/util/utils';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-progress-bar',
    templateUrl: './progress-bar.component.html',
})
export class ProgressBarComponent implements OnInit, OnChanges, OnDestroy {
    @Input() public tooltip: string;
    @Input() public percentage: number;
    @Input() public numerator: number;
    @Input() public denominator: number;

    foregroundColorClass: string;
    backgroundColorClass: string;
    themeSubscription: Subscription;

    constructor(private themeService: ThemeService, private ref: ChangeDetectorRef) {}

    ngOnInit() {
        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe(() => {
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
        switch (this.themeService.getCurrentTheme()) {
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
