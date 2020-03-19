import { Component, Input } from '@angular/core';
import { MIN_POINTS_GREEN, MIN_POINTS_ORANGE } from 'app/app.constants';
import { Result } from 'app/entities/result.model';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss'],
})
export class ResultHistoryComponent {
    @Input() results: Result[];
    @Input() maxScore: number;
    @Input() showPreviousDivider = false;

    resultIcon(result: Result): string {
        if (result.score >= 75) {
            return 'check';
        } else {
            return 'times';
        }
    }

    resultClass(result: Result): string {
        if (result.score >= MIN_POINTS_GREEN) {
            return 'success';
        } else if (result.score >= MIN_POINTS_ORANGE) {
            return 'warning';
        } else {
            return 'danger';
        }
    }

    // TODO: document the implementation of this method --> it is not really obvious
    // TODO: save the return value of this method in the result object (as temp variable) to avoid that this method is invoked all the time
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
