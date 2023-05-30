import dayjs from 'dayjs/esm';
import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoal, LearningGoalProgress, getIcon, getIconTooltip } from 'app/entities/learningGoal.model';

@Component({
    selector: 'jhi-learning-goal-card',
    templateUrl: './learning-goal-card.component.html',
    styleUrls: ['../../../overview/course-exercises/course-exercise-row.scss'],
})
export class LearningGoalCardComponent {
    @Input()
    courseId?: number;
    @Input()
    learningGoal: LearningGoal;
    @Input()
    isPrerequisite: boolean;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(public translateService: TranslateService) {}

    getUserProgress(): LearningGoalProgress {
        if (this.learningGoal.userProgress?.length) {
            return this.learningGoal.userProgress.first()!;
        }
        return { progress: 0, confidence: 0 } as LearningGoalProgress;
    }

    getBadgeClass() {
        if (this.dueDatePassed && !this.isMastered) {
            return 'bg-danger';
        }
        return 'bg-success';
    }

    get progress(): number {
        // The percentage of completed lecture units and participated exercises
        return this.getUserProgress().progress ?? 0;
    }

    get confidence(): number {
        // Confidence level (average score in exercises) in proportion to the threshold value (max. 100 %)
        // Example: If the student’s latest confidence level equals 60 % and the mastery threshold is set to 80 %, the ring would be 75 % full.
        return Math.min(Math.round(((this.getUserProgress().confidence ?? 0) / (this.learningGoal.masteryThreshold ?? 100)) * 100), 100);
    }

    get mastery(): number {
        // Advancement towards mastery as a weighted function of progress and confidence
        const weight = 2 / 3;
        return Math.round((1 - weight) * this.progress + weight * this.confidence);
    }

    get isMastered(): boolean {
        return this.mastery >= 100;
    }

    get dueDatePassed(): boolean {
        return dayjs().isAfter(this.learningGoal.dueDate);
    }
}
