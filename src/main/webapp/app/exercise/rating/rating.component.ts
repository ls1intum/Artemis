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
    readonly isOwnerOfParticipation = input<boolean>();
    readonly starSize = input('24');
    /**
     * `stacked` is the page-level callout used by the exercise result pages.
     * `inline` puts the prompt and the stars on one row for hosts with a column
     * to spare — a side panel, an editor's chrome — and wraps when there is not.
     */
    readonly layout = input<'stacked' | 'inline'>('stacked');

    constructor() {
        // Loads the rating on the first binding and again whenever the result changes to a *different* id: the
        // reference alone changes on every refresh, and refetching then would only repeat the request. The reload is
        // untracked so the participation and account reads inside `loadRating` do not become triggers of their own.
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
        const participation = this.participation();
        if (!result?.id || !participation) {
            return;
        }
        if (!(this.isOwnerOfParticipation() ?? this.accountService.isOwnerOfParticipation(participation))) {
            return;
        }
        this.ratingService.getRating(result.id).subscribe((rating) => {
            this.rating.set(rating ?? 0);
        });
    }

    onRate(event: { oldValue: number; newValue: number }) {
        const result = this.result();
        if (this.disableRating() || !result) {
            return;
        }

        const oldRating = this.rating();
        this.rating.set(event.newValue);

        this.disableRating.set(true);
        let observable: Observable<number>;
        if (oldRating) {
            observable = this.ratingService.updateRating(this.rating(), result.id!);
        } else {
            observable = this.ratingService.createRating(this.rating(), result.id!);
        }

        observable.subscribe((rating) => this.rating.set(rating)).add(() => this.disableRating.set(false));
    }
}
