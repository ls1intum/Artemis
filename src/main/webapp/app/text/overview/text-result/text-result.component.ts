import { Component, effect, input } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextResultBlock } from './text-result-block';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgClass } from '@angular/common';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';

@Component({
    selector: 'jhi-text-result',
    templateUrl: './text-result.component.html',
    styleUrls: ['./text-result.component.scss'],
    imports: [NgClass, UnifiedFeedbackComponent],
})
export class TextResultComponent {
    result = input<Result>();
    course = input<Course>();

    submissionText = '';
    textResults: TextResultBlock[] = [];
    private submission: TextSubmission;

    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;
    private readonly SHA1_REGEX = /^[a-f0-9]{40}$/i;

    constructor() {
        effect(() => {
            const result = this.result();
            if (!result?.submission || !(result.submission as TextSubmission)) {
                return;
            }

            this.submission = result.submission as TextSubmission;
            this.submissionText = this.submission.text || '';
            this.convertTextToResultBlocks(result.feedbacks);
        });
    }

    private convertTextToResultBlocks(feedbacks: Feedback[] = []): void {
        const [referenceBasedFeedback, blockBasedFeedback]: [Feedback[], Feedback[]] = feedbacks.reduce(
            ([refBased, blockBased], elem) => (this.SHA1_REGEX.test(elem.reference!) ? [refBased, [...blockBased, elem]] : [[...refBased, elem], blockBased]),
            [[], []],
        );

        const referenceBasedResultBlocks = referenceBasedFeedback.map(this.feedbackToTextResultBlock, this);
        const blockBasedResultBlocks = blockBasedFeedback.map(this.textBlockToTextResultBlock, this);

        const resultBlocks = ([...referenceBasedResultBlocks, ...blockBasedResultBlocks].filter((elem) => elem !== undefined) as TextResultBlock[]).sort(
            (a, b) => b.startIndex - a.startIndex,
        );

        let nextBlock = resultBlocks.pop();
        let startIndex = 0;
        const endIndex = this.submissionText.length;
        this.textResults = [];

        while (startIndex < endIndex) {
            if (nextBlock && nextBlock.startIndex === startIndex) {
                this.textResults.push(nextBlock);
                startIndex = nextBlock.endIndex;
                nextBlock = resultBlocks.pop();
            } else {
                const endOfSlice = nextBlock ? nextBlock.startIndex : endIndex;
                const slice = this.submissionText.slice(startIndex, endOfSlice);
                const textBlock = new TextBlock();
                textBlock.startIndex = startIndex;
                textBlock.endIndex = endOfSlice;
                textBlock.text = slice;
                const textResultBlock = new TextResultBlock(textBlock);
                this.textResults.push(textResultBlock);
                startIndex = endOfSlice;
            }
        }
    }

    private feedbackToTextResultBlock(feedback: Feedback): TextResultBlock | undefined {
        if (!feedback.reference) {
            return undefined;
        }

        const startIndex = parseInt(feedback.reference, 10);
        if (!isNaN(startIndex) && startIndex >= 0 && startIndex < this.submissionText.length) {
            const textBlock = new TextBlock();
            textBlock.startIndex = startIndex;
            textBlock.endIndex = startIndex + 1;
            textBlock.text = this.submissionText.charAt(startIndex);
            return new TextResultBlock(textBlock, feedback);
        }

        const indexOfReference = this.submissionText.indexOf(feedback.reference);
        if (indexOfReference !== -1) {
            const textBlock = new TextBlock();
            textBlock.text = feedback.reference;
            textBlock.startIndex = indexOfReference;
            textBlock.endIndex = indexOfReference + feedback.reference.length;
            return new TextResultBlock(textBlock, feedback);
        }

        return undefined;
    }

    private textBlockToTextResultBlock(feedback: Feedback): TextResultBlock | undefined {
        if (!feedback.reference) {
            return undefined;
        }

        if (this.submission.blocks) {
            const result = this.submission.blocks.find((block) => block.id === feedback.reference);
            if (result) {
                return new TextResultBlock(result, feedback);
            }
        }

        return undefined;
    }
}
