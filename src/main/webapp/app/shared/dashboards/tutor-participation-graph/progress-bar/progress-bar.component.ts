import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-progress-bar',
    templateUrl: './progress-bar.component.html',
})
export class ProgressBarComponent {
    @Input() public tooltip: string;
    @Input() public percentage: number;
    @Input() public numerator: number;
    @Input() public denominator: number;

    /**
     * Identifies the correct progress bar class between 'bg-danger', 'bg-warning' and 'bg-success' using {@param percentage}.
     * @method
     * @param percentage The completed percentage of the progress bar.
     */
    calculateProgressBarClass(percentage: number): string {
        if (percentage < 50) {
            return 'bg-danger';
        } else if (percentage < 100) {
            return 'bg-warning';
        }

        return 'bg-success';
    }

    /**
     * Sets the progress bar text color 'text-dark' if {@param percentage} is below 100, otherwise 'test-white'.
     * @method
     * @param percentage The completed percentage of the progress bar.
     */
    chooseProgressBarTextColor(percentage: number) {
        if (percentage < 100) {
            return 'text-dark';
        }
        return 'text-white';
    }
}
