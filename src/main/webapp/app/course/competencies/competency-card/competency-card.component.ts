import dayjs from 'dayjs/esm';
import { Component, input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyProgress, CourseCompetency, getIcon, getMastery, getProgress } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-card',
    templateUrl: './competency-card.component.html',
    styleUrls: ['../../../overview/course-exercises/course-exercise-row.scss'],
})
export class CompetencyCardComponent {
    courseId = input<number | undefined>();
    competency = input<CourseCompetency>();
    isPrerequisite = input<boolean>();
    hideProgress = input<boolean>(false);
    noProgressRings = input<boolean>(false);

    protected readonly getIcon = getIcon;

    constructor(public translateService: TranslateService) {}

    getUserProgress(): CompetencyProgress {
        const userProgress = this.competency()?.userProgress?.first();
        if (userProgress) {
            return userProgress;
        }
        return { progress: 0, confidence: 1 } as CompetencyProgress;
    }

    get progress(): number {
        return getProgress(this.getUserProgress());
    }

    get mastery(): number {
        return getMastery(this.getUserProgress());
    }

    get isMastered(): boolean {
        return this.mastery >= 100;
    }

    get softDueDatePassed(): boolean {
        return dayjs().isAfter(this.competency()?.softDueDate);
    }
}
