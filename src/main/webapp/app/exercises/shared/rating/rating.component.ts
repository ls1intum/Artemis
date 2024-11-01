import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { RatingService } from 'app/exercises/shared/rating/rating.service';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-rating',
    templateUrl: './rating.component.html',
    styleUrls: ['./rating.component.scss'],
})
export class RatingComponent implements OnInit, OnChanges {
    public rating: number;
    public disableRating = false;
    @Input() result?: Result;

    constructor(
        private ratingService: RatingService,
        private accountService: AccountService,
    ) {}

    ngOnInit(): void {
        this.loadRating();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['result'] && !changes['result'].isFirstChange()) {
            this.loadRating();
        }
    }

    loadRating() {
        if (!this.result?.id || !this.result.participation || !this.accountService.isOwnerOfParticipation(this.result.participation as StudentParticipation)) {
            return;
        }
        this.ratingService.getRating(this.result.id).subscribe((rating) => {
            this.rating = rating ?? 0;
        });
    }

    /**
     * Update/Create new Rating for the result
     * @param event - starRating component that holds new rating value
     */
    onRate(event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        // block rating to prevent double sending of post request
        if (this.disableRating || !this.result) {
            return;
        }

        const oldRating = this.rating;
        this.rating = event.newValue;

        this.disableRating = true;
        let observable: Observable<number>;
        // set/update feedback on the server
        if (oldRating) {
            observable = this.ratingService.updateRating(this.rating, this.result.id!);
        } else {
            observable = this.ratingService.createRating(this.rating, this.result.id!);
        }

        observable.subscribe((rating) => (this.rating = rating)).add(() => (this.disableRating = false));
    }
}
