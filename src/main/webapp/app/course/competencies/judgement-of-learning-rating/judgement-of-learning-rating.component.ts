import { Component, EventEmitter, Input, Output } from '@angular/core';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { AlertService } from 'app/core/util/alert.service';
import { CompetencyService } from 'app/course/competencies/competency.service';

@Component({
    selector: 'jhi-judgement-of-learning-rating',
    standalone: true,
    imports: [RatingModule, ArtemisSharedCommonModule],
    templateUrl: './judgement-of-learning-rating.component.html',
})
export class JudgementOfLearningRatingComponent {
    @Input() courseId: number | undefined;
    @Input() competencyId: number;
    @Input() rating: number | undefined;
    @Input() mastery: number | undefined;

    @Output() ratingChange = new EventEmitter<number>();

    constructor(
        private competencyService: CompetencyService,
        private alertService: AlertService,
    ) {}

    /**
     * Handle the event when a new rating is selected.
     * @param event - starRating component that holds the new rating value
     */
    onRate(event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        if (this.rating !== undefined || this.courseId === undefined) {
            return;
        }

        const newRating = event.newValue;

        this.competencyService.setJudgementOfLearning(this.courseId, this.competencyId, newRating).subscribe(
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
