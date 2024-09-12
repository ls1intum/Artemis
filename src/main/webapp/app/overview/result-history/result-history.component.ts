import { Component, Input, OnChanges } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercises/shared/result/result.utils';
import { faAngleLeft, faAngleRight, faAnglesLeft, faAnglesRight } from '@fortawesome/free-solid-svg-icons';

export const MAX_RESULT_HISTORY_LENGTH = 5;

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss'],
})
export class ResultHistoryComponent implements OnChanges {
    readonly getTextColorClass = getTextColorClass;
    readonly getResultIconClass = getResultIconClass;
    readonly evaluateTemplateStatus = evaluateTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;
    readonly faAngleLeft = faAngleLeft;
    readonly faAnglesLeft = faAnglesLeft;
    readonly faAngleRight = faAngleRight;
    readonly faAnglesRight = faAnglesRight;

    @Input() results: Result[];
    @Input() exercise: Exercise;
    @Input() isInsideEditor: boolean = false;

    showFutureDivider = false;
    showPreviousDivider = false;
    displayedResults: Result[];
    movedLastRatedResult: boolean;

    leftIndex: number;
    rightIndex: number;
    successfulResultsLength: number;
    selectedResultId: number;

    ngOnChanges(): void {
        this.calculateResultsToDisplay();
    }

    calculateResultsToDisplay() {
        this.showPreviousDivider = this.results.length > MAX_RESULT_HISTORY_LENGTH;
        if (this.exercise?.type === ExerciseType.TEXT) {
            this.displayedResults = this.results.filter((result) => !!result.successful);
        } else {
            this.displayedResults = this.results;
        }
        this.successfulResultsLength = this.displayedResults.length;

        if (this.leftIndex === undefined || this.rightIndex === undefined) {
            this.rightIndex = Math.max(this.successfulResultsLength, MAX_RESULT_HISTORY_LENGTH) - 1;
            this.leftIndex = Math.max(0, this.rightIndex - MAX_RESULT_HISTORY_LENGTH);
        }

        this.showPreviousDivider = this.leftIndex !== 0;

        this.showFutureDivider = this.rightIndex < this.successfulResultsLength;

        if (this.successfulResultsLength > MAX_RESULT_HISTORY_LENGTH) {
            this.displayedResults = this.displayedResults.slice(this.leftIndex, this.rightIndex);

            const lastRatedResult = this.results.filter((result) => result.rated).pop();
            if (!this.displayedResults[0]?.rated && lastRatedResult) {
                this.displayedResults[0] = lastRatedResult;
                this.movedLastRatedResult = true;
            }
        } else {
            this.displayedResults = this.displayedResults.slice(0, this.successfulResultsLength);
        }
    }

    moveLeft() {
        if (!this.isInsideEditor) return;
        if (this.leftIndex <= 0) {
            return;
        }
        this.leftIndex--;
        this.rightIndex--;
        this.calculateResultsToDisplay();
    }

    moveStart() {
        if (!this.isInsideEditor) return;
        this.leftIndex = 0;
        this.rightIndex = MAX_RESULT_HISTORY_LENGTH;
        this.calculateResultsToDisplay();
    }

    moveRight() {
        if (!this.isInsideEditor) return;
        if (this.rightIndex + 1 >= this.results.length) {
            return;
        }
        this.leftIndex++;
        this.rightIndex++;
        this.calculateResultsToDisplay();
    }

    moveEnd() {
        if (!this.isInsideEditor) return;
        this.rightIndex = this.results.length - 1;
        this.leftIndex = this.results.length - MAX_RESULT_HISTORY_LENGTH - 1;
        this.calculateResultsToDisplay();
    }

    protected readonly MAX_RESULT_HISTORY_LENGTH = MAX_RESULT_HISTORY_LENGTH;
}
