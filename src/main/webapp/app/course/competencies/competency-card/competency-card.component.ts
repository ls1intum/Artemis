import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Competency, CompetencyProgress, getIcon, getIconTooltip } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-card',
    templateUrl: './competency-card.component.html',
    styleUrls: ['../../../overview/course-exercises/course-exercise-row.scss'],
})
export class CompetencyCardComponent {
    @Input()
    courseId?: number;
    @Input()
    competency: Competency;
    @Input()
    isPrerequisite: boolean;

    getIcon = getIcon;
    getIconTooltip = getIconTooltip;

    constructor(public translateService: TranslateService) {}

    getUserProgress(): CompetencyProgress {
        if (this.competency.userProgress?.length) {
            return this.competency.userProgress.first()!;
        }
        return { progress: 0, confidence: 0 } as CompetencyProgress;
    }

    get progress(): number {
        // The percentage of completed lecture units and participated exercises
        return this.getUserProgress().progress ?? 0;
    }

    get confidence(): number {
        // Confidence level (average score in exercises) in proportion to the threshold value (max. 100 %)
        // Example: If the student’s latest confidence level equals 60 % and the mastery threshold is set to 80 %, the ring would be 75 % full.
        return Math.min(Math.round(((this.getUserProgress().confidence ?? 0) / (this.competency.masteryThreshold ?? 100)) * 100), 100);
    }

    get mastery(): number {
        // Advancement towards mastery as a weighted function of progress and confidence
        const weight = 2 / 3;
        return Math.round((1 - weight) * this.progress + weight * this.confidence);
    }

    get isMastered(): boolean {
        return this.mastery >= 100;
    }
}
