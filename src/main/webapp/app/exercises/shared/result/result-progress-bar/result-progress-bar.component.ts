import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-result-progress-bar',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './result-progress-bar.component.html',
    styleUrl: './result-progress-bar.component.scss',
})
export class ResultProgressBarComponent {
    isQueueProgressBarAnimated = input.required<boolean>();
    queueProgressBarOpacity = input.required<number>();
    queueProgressBarValue = input.required<number>();

    isBuildProgressBarAnimated = input.required<boolean>();
    buildProgressBarOpacity = input.required<number>();
    buildProgressBarValue = input.required<number>();
}
