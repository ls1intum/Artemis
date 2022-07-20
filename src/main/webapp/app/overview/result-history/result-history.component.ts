import { Component, Input } from '@angular/core';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Result } from 'app/entities/result.model';
import { faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ResultService } from 'app/exercises/shared/result/result.service';

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss'],
})
export class ResultHistoryComponent {
    @Input() results: Result[];
    @Input() showPreviousDivider = false;

    constructor(public resultService: ResultService) {}

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
