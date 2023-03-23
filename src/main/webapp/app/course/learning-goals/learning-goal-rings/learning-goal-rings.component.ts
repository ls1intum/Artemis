import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-learning-goal-rings',
    templateUrl: './learning-goal-rings.component.html',
    styleUrls: ['./learning-goal-rings.component.scss'],
})
export class LearningGoalRingsComponent {
    @Input() progress = 0;
    @Input() confidence = 0;
    @Input() mastery = 0;

    get progressPercentage(): number {
        return this.percentageRange(this.progress);
    }

    get confidencePercentage(): number {
        return this.percentageRange(this.confidence);
    }

    get masteryPercentage(): number {
        return this.percentageRange(this.mastery);
    }

    /**
     * Restrict the value to the percentage range (between 0 and 100)
     * @param value
     */
    percentageRange(value: number): number {
        return Math.min(Math.max(value, 0), 100);
    }
}
