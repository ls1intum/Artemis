import { Component, OnChanges, input, signal } from '@angular/core';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercise/result/result.utils';
import { NgClass, NgStyle } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResultComponent } from '../../exercise/result/result.component';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

export const MAX_RESULT_HISTORY_LENGTH = 5;

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss'],
    imports: [NgClass, FaIconComponent, ResultComponent, NgStyle],
})
export class ResultHistoryComponent implements OnChanges {
    readonly getTextColorClass = getTextColorClass;
    readonly getResultIconClass = getResultIconClass;
    readonly evaluateTemplateStatus = evaluateTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;

    results = input.required<Result[]>();
    participationInput = input<Participation>();
    participation = signal<Participation | undefined>(undefined);
    exercise = input<Exercise>();
    selectedResultId = input<number>();

    showPreviousDivider = false;
    displayedResults: Result[];
    movedLastRatedResult: boolean;

    private logDebug(event: string, data: Record<string, unknown> = {}) {
        if (typeof window === 'undefined') {
            return;
        }
        const win = window as any;
        win.__artemisdebug = win.__artemisdebug || {};
        win.__artemisdebug.resultHistory = win.__artemisdebug.resultHistory || [];
        win.__artemisdebug.resultHistory.push({
            ts: new Date().toISOString(),
            event,
            ...data,
        });
    }

    ngOnChanges() {
        this.logDebug('[ResultHistory] ngOnChanges - before compute', {
            resultsLength: this.results()?.length,
            exerciseId: this.exercise()?.id,
            exerciseType: this.exercise()?.type,
            selectedResultId: this.selectedResultId(),
        });
        this.showPreviousDivider = this.results().length > MAX_RESULT_HISTORY_LENGTH;
        if (this.exercise()?.type === ExerciseType.TEXT || this.exercise()?.type === ExerciseType.MODELING) {
            this.displayedResults = this.results().filter((result) => result.successful !== undefined);
        } else {
            this.displayedResults = this.results();
        }
        const successfulResultsLength = this.displayedResults.length;
        if (successfulResultsLength > MAX_RESULT_HISTORY_LENGTH) {
            this.displayedResults = this.displayedResults.slice(successfulResultsLength - MAX_RESULT_HISTORY_LENGTH);
            const lastRatedResult = this.results()
                .filter((result) => result.rated)
                .last();
            if (!this.displayedResults.first()?.rated && lastRatedResult) {
                this.displayedResults[0] = lastRatedResult;
                this.movedLastRatedResult = true;
            }
        }
        this.participation.set(this.participationInput() ?? this.displayedResults?.[0]?.submission?.participation);
    }
}
