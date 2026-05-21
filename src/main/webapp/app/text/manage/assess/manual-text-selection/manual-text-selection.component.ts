import { Component, effect, inject, input, output, signal } from '@angular/core';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlockType } from 'app/text/shared/entities/text-block.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextBlockRefGroup } from 'app/text/manage/assess/manual-textblock-selection/manual-textblock-selection.component';

export type wordSelection = {
    word: string;
    index: number;
};

const LINEBREAK = /\n/g;
const SPACE = ' ';

@Component({
    selector: 'jhi-manual-text-selection',
    templateUrl: './manual-text-selection.component.html',
    styleUrls: ['./manual-text-selection.component.scss'],
})
export class ManualTextSelectionComponent {
    protected route = inject(ActivatedRoute);
    textAssessmentAnalytics = inject(TextAssessmentAnalytics);

    textBlockRefGroup = input.required<TextBlockRefGroup>();
    submission = input.required<TextSubmission>();
    didSelectWord = output<wordSelection[]>();

    public submissionWords = signal<string[] | undefined>(undefined);
    public currentWordIndex: number;
    public selectedWords = new Array<wordSelection>();
    public ready = false;

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);

        // Effect to compute submissionWords when textBlockRefGroup or submission changes
        effect(() => {
            const textBlockRefGroupValue = this.textBlockRefGroup();
            const submissionValue = this.submission();
            if (textBlockRefGroupValue && submissionValue) {
                // Since some words are only separated through linebreaks, the linebreaks are replaced by a linebreak with an additional space, in order to split the words by spaces.
                this.submissionWords.set(textBlockRefGroupValue.getText(submissionValue).replace(LINEBREAK, '\n ').split(SPACE));
            }
        });
    }

    calculateIndex(index: number): void {
        let result = this.textBlockRefGroup().startIndex!;
        const words = this.submissionWords();
        for (let i = 0; i < index; i++) {
            const space = 1;
            result += words![i].length + space;

            const wordContainsLinebreak = words![i].search(/\n+/g) !== -1;
            if (wordContainsLinebreak) {
                result--;
            }
        }
        this.currentWordIndex = result;
    }

    selectWord(word: string): void {
        const canSelectWord = this.selectedWords.length < 2;
        if (canSelectWord) {
            this.selectedWords.push({ word, index: this.currentWordIndex });
        }

        const textBlockSelected = this.selectedWords.length === 2;
        if (textBlockSelected) {
            this.ready = true;

            const lastWordClickedFirst = this.selectedWords[0].index > this.selectedWords[1].index;
            if (lastWordClickedFirst) {
                this.selectedWords.reverse();
            }
        }

        if (this.ready) {
            this.didSelectWord.emit(this.selectedWords);
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL);
        }
    }
}
