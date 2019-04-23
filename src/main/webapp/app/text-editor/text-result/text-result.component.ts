import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback';
import { TextSubmission } from 'app/entities/text-submission';
import { Result } from 'app/entities/result';
import { TextResultBlock } from './text-result-block';

@Component({
    selector: 'jhi-text-result',
    templateUrl: './text-result.component.html',
    styleUrls: ['./text-result.component.scss'],
})
export class TextResultComponent {
    public submissionText: string;

    public textResults: TextResultBlock[];

    @Input()
    public set result(result: Result) {
        if (!result || !result.submission || !(result.submission as TextSubmission)) {
            return;
        }

        this.submissionText = (result.submission as TextSubmission).text;
        this.convertTextToResultBlocks(result.feedbacks);
    }

    private convertTextToResultBlocks(feedbacks: Feedback[] = []): void {
        const resultBlocks = feedbacks.map(this.feedbackToTextResultBlock, this).sort((a, b) => b.startIndex - a.startIndex);

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
                const textResultBlock = new TextResultBlock(slice, startIndex);
                this.textResults.push(textResultBlock);
                startIndex = endOfSlice;
            }
        }
    }

    private feedbackToTextResultBlock(feedback: Feedback): TextResultBlock {
        const indexOfReference = this.submissionText.indexOf(feedback.reference);
        return new TextResultBlock(feedback.reference, indexOfReference, feedback);
    }
}
