import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

@Component({
    selector: 'jhi-judgement-of-learning-rating',
    standalone: true,
    imports: [RatingModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule],
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
