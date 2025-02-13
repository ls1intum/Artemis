import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';

import { AlertService } from 'app/core/util/alert.service';

import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

@Component({
    selector: 'jhi-judgement-of-learning-rating',
    imports: [StarRatingComponent, HelpIconComponent],
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
