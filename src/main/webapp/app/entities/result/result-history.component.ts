import { Component, Input } from '@angular/core';
import { Result } from './';
import { MIN_POINTS_GREEN, MIN_POINTS_ORANGE } from 'app/app.constants';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss']
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

    absoluteResult(result: Result): number {
        if (!result.resultString) {
            return 0;
        }
        if (result.resultString.indexOf('of') === -1) {
            return parseInt(result.resultString.slice(0, result.resultString.indexOf('points')), 10);
        } else {
            return parseInt(result.resultString.slice(0, result.resultString.indexOf('of')), 10);
        }
    }
}
