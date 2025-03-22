import dayjs from 'dayjs/esm';
import { Component, inject, input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyProgress, CourseCompetency, getIcon, getMastery, getProgress } from 'app/atlas/shared/entities/competency.model';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { CompetencyRingsComponent } from 'app/atlas/shared/competency-rings/competency-rings.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-competency-card',
    templateUrl: './competency-card.component.html',
    styleUrls: ['../../../overview/course-exercises/course-exercise-row.scss'],
    imports: [RouterLink, FaIconComponent, NgbTooltip, TranslateDirective, NgClass, CompetencyRingsComponent, ArtemisTranslatePipe, ArtemisTimeAgoPipe, HtmlForMarkdownPipe],
})
export class CompetencyCardComponent {
    translateService = inject(TranslateService);

    courseId = input<number | undefined>();
    competency = input<CourseCompetency>();
    isPrerequisite = input<boolean>();
    hideProgress = input<boolean>(false);
    noProgressRings = input<boolean>(false);

    protected readonly getIcon = getIcon;

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
