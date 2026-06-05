import { Component, computed, input } from '@angular/core';
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
export class ResultHistoryComponent {
    readonly getTextColorClass = getTextColorClass;
    readonly getResultIconClass = getResultIconClass;
    readonly evaluateTemplateStatus = evaluateTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;

    results = input.required<Result[]>();
    participationInput = input<Participation>();
    exercise = input<Exercise>();
    selectedResultId = input<number>();

    readonly showPreviousDivider = computed(() => this.results().length > MAX_RESULT_HISTORY_LENGTH);

    private readonly displayedResultsState = computed(() => {
        let displayedResults: Result[];
        if (this.exercise()?.type === ExerciseType.TEXT || this.exercise()?.type === ExerciseType.MODELING) {
            displayedResults = this.results().filter((result) => result.successful !== undefined);
        } else {
            displayedResults = this.results();
        }
        let movedLastRatedResult = false;
        const successfulResultsLength = displayedResults.length;
        if (successfulResultsLength > MAX_RESULT_HISTORY_LENGTH) {
            displayedResults = displayedResults.slice(successfulResultsLength - MAX_RESULT_HISTORY_LENGTH);
            const lastRatedResult = this.results()
                .filter((result) => result.rated)
                .last();
            if (!displayedResults.first()?.rated && lastRatedResult) {
                displayedResults = [lastRatedResult, ...displayedResults.slice(1)];
                movedLastRatedResult = true;
            }
        }
        return { displayedResults, movedLastRatedResult };
    });

    readonly displayedResults = computed(() => this.displayedResultsState().displayedResults);
    readonly movedLastRatedResult = computed(() => this.displayedResultsState().movedLastRatedResult);

    readonly participation = computed<Participation | undefined>(() => this.participationInput() ?? this.displayedResults()?.[0]?.submission?.participation);
}
