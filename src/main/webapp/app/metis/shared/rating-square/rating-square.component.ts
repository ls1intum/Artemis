import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ratingSeverity } from 'app/metis/shared/rating-scale.util';

/**
 * A small inline square that shows one rating of a comprehension check in the colour of its step on the rating
 * scale, from no understanding (`lowest`) to full understanding (`highest`); the colours are the five
 * `--rating-scale-*` theme properties. Without a score it shows an en dash in a dashed frame, which marks a
 * question that has no rating yet. The dashboard, the question review and the student's results use it.
 */
@Component({
    selector: 'jhi-rating-square',
    templateUrl: './rating-square.component.html',
    styleUrl: './rating-square.component.scss',
    imports: [ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RatingSquareComponent {
    /** The rating, from 1 to `points`; undefined while the answer has no rating. */
    readonly score = input<number | undefined>();
    /** The number of points of the Likert scale the rating belongs to. */
    readonly points = input(5);
    /** `small` fits into a table cell, `normal` sits next to running text. */
    readonly size = input<'small' | 'normal'>('normal');

    /** The step of the rating scale the score falls on, or undefined without a score. */
    protected readonly severity = computed(() => {
        const score = this.score();
        return score === undefined ? undefined : ratingSeverity(score, this.points());
    });

    /** The BEM classes of the square: its step on the scale (or `unrated`) and its size. */
    protected readonly classes = computed(() => {
        const classes = ['rating-square', `rating-square--${this.severity() ?? 'unrated'}`];
        if (this.size() === 'small') {
            classes.push('rating-square--small');
        }
        return classes.join(' ');
    });

    /** The accessible name: the rating and its scale, or that there is no rating yet. */
    protected readonly labelKey = computed(() => (this.score() === undefined ? 'artemisApp.metis.ratingSquare.unrated' : 'artemisApp.metis.ratingSquare.label'));
    protected readonly labelParams = computed(() => ({ score: this.score(), points: this.points() }));
}
