import { Component, Input, OnChanges, OnInit, SimpleChanges, inject } from '@angular/core';
import { RatingService } from 'app/exercise/rating/rating.service';
import { StarRatingComponent } from 'app/exercise/rating/star-rating/star-rating.component';
import { Result } from 'app/exercise/entities/result.model';
import { StudentParticipation } from 'app/exercise/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { Observable } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-rating',
    templateUrl: './rating.component.html',
    styleUrls: ['./rating.component.scss'],
    imports: [TranslateDirective, StarRatingComponent],
})
export class RatingComponent implements OnInit, OnChanges {
    private ratingService = inject(RatingService);
    private accountService = inject(AccountService);

    public rating: number;
    public disableRating = false;
    private previousResultId?: number;

    @Input() result?: Result;

    ngOnInit(): void {
        this.loadRating();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['result'] && changes['result'].currentValue?.id !== this.previousResultId) {
            this.previousResultId = changes['result'].currentValue?.id;
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
