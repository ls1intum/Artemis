import { Component } from '@angular/core';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-judgement-of-learning-rating',
    standalone: true,
    imports: [RatingModule, ArtemisSharedCommonModule],
    templateUrl: './judgement-of-learning-rating.component.html',
})
export class JudgementOfLearningRatingComponent {
    rating: number | undefined;
    artemisRating: number | undefined;

    /**
     * Create new master rating for competency and receive the artemis rating
     * @param event - starRating component that holds new rating value
     */
    onRate(event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        if (this.rating !== undefined) {
            return;
        }

        this.rating = event.newValue;
        this.artemisRating = Math.random() * 5;
    }
}
