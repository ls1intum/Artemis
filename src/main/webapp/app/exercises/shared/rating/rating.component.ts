import { Component, Input, OnInit } from '@angular/core';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { Result } from 'app/entities/result.model';
import { Rating } from 'app/entities/rating.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-rating',
    templateUrl: './rating.component.html',
    styleUrls: ['./rating.component.scss'],
})
export class RatingComponent implements OnInit {
    public rating: Rating;
    public disableRating = false;
    @Input() result?: Result;

    constructor(private ratingService: RatingService, private accountService: AccountService) {}

    ngOnInit(): void {
        if (!this.result || !this.result.id || !this.result.participation || !this.accountService.isOwnerOfParticipation(this.result.participation as StudentParticipation)) {
            return;
        }

        this.ratingService.getRating(this.result.id).subscribe((rating) => {
            if (rating) {
                this.rating = rating;
            } else {
                this.rating = new Rating(this.result, 0);
            }
        });
    }

    /**
     * Update/Create new Rating for the result
     * @param event - starRating component that holds new rating value
     */
    onRate(event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        // block rating to prevent double sending of post request
        if (this.disableRating || !this.rating.result) {
            return;
        }

        // update feedback locally
        this.rating.rating = event.newValue;

        // set/update feedback on the server
        if (this.rating.id) {
            this.ratingService.updateRating(this.rating).subscribe((rating) => {
                this.rating = rating;
            });
        } else {
            this.disableRating = true;
            this.ratingService.createRating(this.rating).subscribe((rating) => {
                this.rating = rating;
                this.disableRating = false;
            });
        }
    }
}
