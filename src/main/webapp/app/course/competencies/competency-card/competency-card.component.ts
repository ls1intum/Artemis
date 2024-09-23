import dayjs from 'dayjs/esm';
import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Competency, CompetencyProgress, getIcon, getMastery, getProgress } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-card',
    templateUrl: './competency-card.component.html',
    styleUrls: ['../../../overview/course-exercises/course-exercise-row.scss'],
})
export class CompetencyCardComponent {
    @Input()
    courseId: number | undefined;
    @Input()
    competency: Competency;
    @Input()
    isPrerequisite: boolean;
    @Input()
    hideProgress = false;
    @Input()
    noProgressRings = false;

    protected readonly getIcon = getIcon;

    constructor(public translateService: TranslateService) {}

    getUserProgress(): CompetencyProgress {
        if (this.competency.userProgress?.length) {
            return this.competency.userProgress.first()!;
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
        return dayjs().isAfter(this.competency.softDueDate);
    }
}
