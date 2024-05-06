import { Component, Input } from '@angular/core';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-progress-bar',
    templateUrl: './progress-bar.component.html',
    styleUrl: './progress-bar.component.scss',
})
export class ProgressBarComponent {
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
        return (this.currentValue / this.maxValue) * 100;
    }
}
