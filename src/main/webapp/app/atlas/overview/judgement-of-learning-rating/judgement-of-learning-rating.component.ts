import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';

import { AlertService } from 'app/shared/service/alert.service';

import { CourseCompetencyService } from 'app/atlas/shared/course-competency.service';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-judgement-of-learning-rating',
    imports: [StarRatingComponent, HelpIconComponent, TranslateDirective],
    templateUrl: './judgement-of-learning-rating.component.html',
})
export class JudgementOfLearningRatingComponent {
    private courseCompetencyService = inject(CourseCompetencyService);
    private alertService = inject(AlertService);

    @Input() courseId: number | undefined;
    @Input() competencyId: number;
    @Input() rating: number | undefined;
    @Input() mastery: number | undefined;

    @Output() ratingChange = new EventEmitter<number>();

    /**
     * Handle the event when a new rating is selected.
     * @param event - starRating component that holds the new rating value
     */
    onRate(event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        if (this.rating !== undefined || this.courseId === undefined) {
            return;
        }

        const newRating = event.newValue;

        this.courseCompetencyService.setJudgementOfLearning(this.courseId, this.competencyId, newRating).subscribe(
            () => {
                this.rating = newRating;
                this.ratingChange.emit(newRating);
            },
            () => {
                this.alertService.error('artemisApp.courseStudentDashboard.judgementOfLearning.error');
            },
        );
    }
}
