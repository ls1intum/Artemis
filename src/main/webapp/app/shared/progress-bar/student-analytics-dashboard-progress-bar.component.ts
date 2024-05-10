import { Component, Input } from '@angular/core';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-student-analytics-dashboard-progress-bar',
    templateUrl: './student-analytics-dashboard-progress-bar.component.html',
    styleUrl: './student-analytics-dashboard-progress-bar.component.scss',
})
export class StudentAnalyticsDashboardProgressBarComponent {
    @Input() size: 'small' | 'medium' | 'large' = 'medium';
    @Input() maxValue: number = 100;
    @Input() icon: IconDefinition | null = null;
    @Input() progressColor: string = '#52FF00'; // Default yellow
    @Input() backgroundColor: string = '#D9D9D9'; // Default light gray
    @Input() currentValue: number = 0;
    @Input() inline: boolean = false;
    @Input() showLabel: boolean = false;
    @Input() title: string = '';

    get percentage(): number {
        if (this.maxValue === 0) {
            return 0;
        }
        return Math.min((this.currentValue / this.maxValue) * 100, 100);
    }
}
