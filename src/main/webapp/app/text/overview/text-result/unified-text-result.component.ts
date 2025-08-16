import { Component, Input } from '@angular/core';
import { Feedback, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextResultBlock } from './text-result-block';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgClass } from '@angular/common';
import { FeedbackIconType, UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback';

@Component({
    selector: 'jhi-unified-text-result',
    templateUrl: './unified-text-result.component.html',
    styleUrls: ['./unified-text-result.component.scss'],
    imports: [NgClass, UnifiedFeedbackComponent],
})
export class UnifiedTextResultComponent {
    public submissionText: string;
    public textResults: TextResultBlock[];
    private submission: TextSubmission;

    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;
    private readonly SHA1_REGEX = /^[a-f0-9]{40}$/i;

    @Input()
    public set result(result: Result) {
        if (!result || !result.submission || !(result.submission as TextSubmission)) {
            return;
        }

        this.submission = result.submission as TextSubmission;
        this.submissionText = this.submission.text || '';
        this.convertTextToResultBlocks(result.feedbacks);
    }
    @Input()
    course?: Course;

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
        if (isNaN(startIndex) || startIndex < 0 || startIndex >= this.submissionText.length) {
            return undefined;
        }

        const textBlock = new TextBlock();
        textBlock.startIndex = startIndex;
        textBlock.endIndex = startIndex + 1;
        textBlock.text = this.submissionText.charAt(startIndex);

        return new TextResultBlock(textBlock, feedback);
    }

    private textBlockToTextResultBlock(feedback: Feedback): TextResultBlock | undefined {
        if (!feedback.reference) {
            return undefined;
        }

        const textBlock = new TextBlock();
        textBlock.startIndex = 0;
        textBlock.endIndex = this.submissionText.length;
        textBlock.text = this.submissionText;

        return new TextResultBlock(textBlock, feedback);
    }

    // TODO: This is a temporary solution to get the feedback icon type. We need to find a better way to do this.
    getFeedbackIconType(feedback: Feedback): FeedbackIconType {
        if (feedback.credits && feedback.credits > 0) {
            return 'success';
        } else if (feedback.credits && feedback.credits < 0) {
            return 'error';
        } else {
            // For 0 credits, check if it's positive feedback (encouraging) or needs improvement
            return feedback.positive ? 'success' : 'retry';
        }
    }

    getFeedbackTitle(feedback: Feedback): string {
        if (feedback.text) {
            return feedback.text;
        }
        if (feedback.credits && feedback.credits > 0) {
            return 'Good job!';
        } else if (feedback.credits && feedback.credits < 0) {
            return 'Incorrect';
        } else {
            // For 0 credits, check if it's positive feedback (encouraging) or needs improvement
            return feedback.positive ? 'Good effort!' : 'Needs revision';
        }
    }
}
