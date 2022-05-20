import { Component, Input, ViewEncapsulation } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

import { ExerciseHint, HintType } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintResponse, ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { StarRatingComponent } from 'app/exercises/shared/rating/star-rating/star-rating.component';

/**
 * This component is a modal that shows the exercise's hints.
 */
@Component({
    selector: 'jhi-exercise-hint-expandable',
    templateUrl: './exercise-hint-expandable.component.html',
    styleUrls: ['./exercise-hint-expandable.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ExerciseHintExpandableComponent {
    @Input() exerciseHint: ExerciseHint;
    @Input() hasUsed: boolean;

    expanded = false;
    isLoading = false;

    faQuestionCircle = faQuestionCircle;

    readonly HintType = HintType;

    constructor(private exerciseHintService: ExerciseHintService) {}

    displayHintContent() {
        console.log(this.exerciseHint.currentUserRating);
        this.expanded = true;

        if (this.hasUsed) {
            // the hint already contains the content
            return;
        }

        this.isLoading = true;
        this.exerciseHintService.activateExerciseHint(this.exerciseHint!.exercise!.id!, this.exerciseHint!.id!).subscribe((res: ExerciseHintResponse) => {
            this.exerciseHint = res.body!;
            this.hasUsed = true;
            this.isLoading = false;
        });
    }

    collapseContent() {
        this.expanded = false;
    }

    onRate(event: { oldValue: number; newValue: number; starRating: StarRatingComponent }) {
        this.exerciseHintService.rateExerciseHint(this.exerciseHint!.exercise!.id!, this.exerciseHint!.id!, event.newValue).subscribe(() => {
            this.exerciseHint.currentUserRating = event.newValue;
        });
    }
}
