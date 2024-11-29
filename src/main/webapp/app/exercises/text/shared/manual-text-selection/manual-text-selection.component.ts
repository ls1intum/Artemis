import { Component, effect, inject, input, output } from '@angular/core';
import { TextAssessmentEventType } from 'app/entities/text/text-assesment-event.model';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text/text-block.model';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { TextBlockRefGroup } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';

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
    submission = input<TextSubmission>();
    didSelectWord = output<wordSelection[]>();
    words = input<TextBlockRefGroup>();
    public submissionWords: string[] | undefined;

    public currentWordIndex: number;
    public selectedWords = new Array<wordSelection>();
    public ready = false;

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);

        effect(() => {
            const textBlockRefGroup = this.words();
            if (textBlockRefGroup) {
                const submission = this.submission();
                if (submission) {
                    this.submissionWords = textBlockRefGroup.getText(submission).replace(LINEBREAK, '\n ').split(SPACE);
                }
            }
        });
    }

    calculateIndex(index: number): void {
        let result = this.textBlockRefGroup().startIndex!;
        for (let i = 0; i < index; i++) {
            const space = 1;
            result += this.submissionWords![i].length + space;

            const wordContainsLinebreak = this.submissionWords![i].search(/\n+/g) !== -1;
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
