import { Component, Input } from '@angular/core';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { StarRatingComponent } from 'ng-starrating';
import { Result } from 'app/entities/result.model';
import { Rating } from 'app/entities/rating.model';

@Component({
    selector: 'jhi-rating',
    providers: [RatingService],
    templateUrl: './rating.component.html',
    styleUrls: ['./rating.component.scss'],
})
export class RatingComponent {
    public rating: Rating;
    private _result: Result;

    constructor(public ratingService: RatingService) {}

    /**
     * Result Input of the result that the rating is for
     * @param result
     */
    @Input()
    public set result(result: Result) {
        if (!result || !result.submission) {
            return;
        }
        this._result = result;
        this.ratingService.getRating(result.id).subscribe(
            (rating) => {
                this.rating = rating;
            },
            () => {
                this.rating = new Rating(result, 0);
            },
        );
    }

    /**
     * Update/Create new Rating for the result
     * @param $event - starRating component that holds new rating value
     */
    onRate($event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        // update feedback locally
        this.rating.rating = $event.newValue;

        // set/update feedback on the server
        if (this.rating.id) {
            this.ratingService.updateRating(this.rating).subscribe((rating) => {
                this.rating = rating;
            });
        } else {
            this.ratingService.createRating(this.rating).subscribe((rating) => {
                this.rating = rating;
            });
        }
    }
}
