import { Component, Input, OnChanges } from '@angular/core';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Result } from 'app/entities/result.model';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';

export const MAX_RESULT_HISTORY_LENGTH = 5;

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss'],
})
export class ResultHistoryComponent implements OnChanges {
    @Input() results: Result[];
    @Input() exercise: Exercise;

    showPreviousDivider = false;
    displayedResults: Result[];
    movedLastRatedResult: boolean;

    ngOnChanges(): void {
        this.showPreviousDivider = this.results.length > MAX_RESULT_HISTORY_LENGTH;

        if (this.results.length <= MAX_RESULT_HISTORY_LENGTH) {
            this.displayedResults = this.results;
        } else {
            this.displayedResults = this.results.slice(this.results.length - MAX_RESULT_HISTORY_LENGTH);

            const lastRatedResult = this.results.filter((result) => result.rated).last();
            if (!this.displayedResults.first()?.rated && lastRatedResult) {
                this.displayedResults[0] = lastRatedResult;
                this.movedLastRatedResult = true;
            }
        }
    }

    /**
     * get string for icon if score bigger than 80
     * @param {Result} result
     * @return {string} icon
     */
    resultIcon(result: Result): IconProp {
        if (result.score && result.score >= MIN_SCORE_GREEN) {
            return faCheck;
        } else {
            return faTimes;
        }
    }

    /**
     * get colour class depending on score
     * @param {Result} result
     * @return {string}
     */
    resultClass(result: Result): string {
        if (result.score && result.score >= MIN_SCORE_GREEN) {
            return 'success';
        } else if (result.score && result.score >= MIN_SCORE_ORANGE) {
            return 'warning';
        } else {
            return 'danger';
        }
    }
}
