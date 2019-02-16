import { Component, Input, OnInit } from '@angular/core';
import { Result } from './';
import { MIN_POINTS_GREEN, MIN_POINTS_ORANGE } from 'app/app.constants';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss']
})
export class ResultHistoryComponent implements OnInit {
    @Input() results: Result[];
    @Input() maxScore: number;

    constructor() {
    }

    ngOnInit(): void {
    }

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
        return parseInt(result.resultString.slice(0, result.resultString.indexOf('of')), 10);
    }
}
