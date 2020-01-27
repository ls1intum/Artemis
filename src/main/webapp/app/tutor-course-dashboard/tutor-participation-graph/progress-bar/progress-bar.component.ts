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

    calculateProgressBarClass(percentage: number): string {
        if (percentage < 50) {
            return 'bg-danger';
        } else if (percentage < 100) {
            return 'bg-warning';
        }

        return 'bg-success';
    }
    chooseProgressBarTextColor(percentage: number) {
        if (percentage < 100) {
            return 'text-dark';
        }
        return 'text-white';
    }
}
