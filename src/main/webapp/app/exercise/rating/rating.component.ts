import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { RatingService } from 'app/assessment/shared/services/rating.service';
import { StarRatingComponent } from 'app/assessment/manage/rating/star-rating/star-rating.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { Observable } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-rating',
    templateUrl: './rating.component.html',
    styleUrls: ['./rating.component.scss'],
    imports: [TranslateDirective, StarRatingComponent],
})
export class RatingComponent {
    private ratingService = inject(RatingService);
    private accountService = inject(AccountService);

    public readonly rating = signal<number>(undefined!);
    public readonly disableRating = signal(false);
    private previousResultId?: number;

    readonly result = input<Result>();
    participation = input.required<StudentParticipation>();

    constructor() {
        // Replaces both ngOnInit and ngOnChanges: load the rating on the initial binding and reload it whenever the
        // result changes to a *different* id. The effect's first run handles the initial load (so a separate ngOnInit
        // is no longer needed — it would only duplicate the request). previousResultId guards against reloading when
        // the result reference changes but its id does not (the same guard the former hook applied). The reload runs
        // untracked so participation()/account reads inside loadRating() are not themselves triggers.
        effect(() => {
            const result = this.result();
            untracked(() => {
                if (result?.id !== this.previousResultId) {
                    this.previousResultId = result?.id;
                    this.loadRating();
                }
            });
        });
    }

    loadRating() {
        const result = this.result();
        if (!result?.id || !this.participation() || !this.accountService.isOwnerOfParticipation(this.participation())) {
            return;
        }
        this.ratingService.getRating(result.id).subscribe((rating) => {
            this.rating.set(rating ?? 0);
        });
    }

    /**
     * Update/Create new Rating for the result
     * @param event - starRating component that holds new rating value
     */
    onRate(event: { oldValue: number; newValue: number }) {
        // block rating to prevent double sending of post request
        const result = this.result();
        if (this.disableRating() || !result) {
            return;
        }

        const oldRating = this.rating();
        this.rating.set(event.newValue);

        this.disableRating.set(true);
        let observable: Observable<number>;
        // set/update feedback on the server
        if (oldRating) {
            observable = this.ratingService.updateRating(this.rating(), result.id!);
        } else {
            observable = this.ratingService.createRating(this.rating(), result.id!);
        }

        observable.subscribe((rating) => this.rating.set(rating)).add(() => this.disableRating.set(false));
    }
}
