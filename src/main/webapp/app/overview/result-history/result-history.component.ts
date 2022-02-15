import { Component, Input } from '@angular/core';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Result } from 'app/entities/result.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss'],
})
export class ResultHistoryComponent {
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

    @Input() results: Result[];
    @Input() maxScore?: number;
    @Input() showPreviousDivider = false;

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

    /**
     * return number of points from resultString
     * @param {Result} result
     * @return {number | null}
     * // TODO: document the implementation of this method --> it is not really obvious
     * // TODO: save the return value of this method in the result object (as temp variable) to avoid that this method is invoked all the time
     */
    absoluteResult(result: Result): number | null {
        if (!result.resultString) {
            return 0;
        }
        if (result.resultString && (result.resultString.indexOf('failed') !== -1 || result.resultString.indexOf('passed')) !== -1) {
            return null;
        }
        if (result.resultString.indexOf('of') === -1) {
            if (result.resultString.indexOf('points') === -1) {
                return 0;
            }
            return parseInt(result.resultString.slice(0, result.resultString.indexOf('points')), 10);
        }
        return parseInt(result.resultString.slice(0, result.resultString.indexOf('of')), 10);
    }
}
