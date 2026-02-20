import { Component, Input, OnChanges, OnInit, SimpleChanges, inject, input } from '@angular/core';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
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
    participation = input.required<StudentParticipation>();

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
        if (!this.result?.id || !this.participation() || !this.accountService.isOwnerOfParticipation(this.participation())) {
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
    onRate(event: { oldValue: number; newValue: number }) {
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
