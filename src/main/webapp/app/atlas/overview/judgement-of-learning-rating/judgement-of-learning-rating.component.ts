import { Component, inject, input, model, signal } from '@angular/core';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { NgIf } from '@angular/common';

import { AlertService } from 'app/shared/service/alert.service';

import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-judgement-of-learning-rating',
    standalone: true,
    imports: [NgIf, StarRatingComponent, HelpIconComponent, TranslateDirective],
    templateUrl: './judgement-of-learning-rating.component.html',
})
export class JudgementOfLearningRatingComponent {
    private courseCompetencyService = inject(CourseCompetencyService);
    private alertService = inject(AlertService);

    courseId = input.required<number>();
    competencyId = input.required<number>();
    rating = model<number>();
    mastery = input<number>();
    readonly helpText = signal<string>('artemisApp.courseStudentDashboard.judgementOfLearning.info');

    /**
     * Handle the event when a new rating is selected.
     * @param event - starRating component that holds the new rating value
     */
    onRate(event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        if (this.rating() !== undefined || this.courseId() === undefined) {
            return;
        }

        const newRating = event.newValue;

        this.courseCompetencyService.setJudgementOfLearning(this.courseId(), this.competencyId(), newRating).subscribe({
            next: () => {
                this.rating.set(newRating);
            },
            error: () => {
                this.alertService.error('artemisApp.courseStudentDashboard.judgementOfLearning.error');
            },
        });
    }
}
