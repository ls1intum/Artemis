import { Component, Input } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { TextResultBlock } from './text-result-block';
import { TranslateService } from '@ngx-translate/core';
import { TextBlock } from 'app/entities/text-block.model';
import { StarRatingComponent } from 'ng-starrating';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { Rating } from 'app/entities/rating.model';

@Component({
    selector: 'jhi-text-result',
    templateUrl: './text-result.component.html',
    styleUrls: ['./text-result.component.scss'],
})
export class TextResultComponent {
    public submissionText: string;

    public textResults: TextResultBlock[];
    private submission: TextSubmission;
    private feedbackRating: Rating[] = [];

    private readonly sha1Regex = /^[a-f0-9]{40}$/i;

    @Input()
    public set result(result: Result) {
        if (!result || !result.submission || !(result.submission as TextSubmission)) {
            return;
        }

        this.submission = result.submission as TextSubmission;
        this.submissionText = this.submission.text || '';
        const feedbackIds = result.feedbacks.map((feedback) => feedback.id);
        this.textService.getRating(feedbackIds).subscribe((ratings) => {
            for (const rating of ratings) {
                this.feedbackRating.push(new Rating(rating.id, rating.feedback.id, rating.rating));
            }
            this.convertTextToResultBlocks(result.feedbacks);
        });
    }

    constructor(private translateService: TranslateService, private textService: TextEditorService) {}

    private convertTextToResultBlocks(feedbacks: Feedback[] = []): void {
        const [referenceBasedFeedback, blockBasedFeedback]: [Feedback[], Feedback[]] = feedbacks.reduce(
            ([refBased, blockBased], elem) => (this.sha1Regex.test(elem.reference!) ? [refBased, [...blockBased, elem]] : [[...refBased, elem], blockBased]),
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
        const reference = feedback.reference;
        if (!reference) {
            return undefined;
        }

        const indexOfReference = this.submissionText.indexOf(reference);

        const textBlock = new TextBlock();
        textBlock.text = reference;
        textBlock.startIndex = indexOfReference;
        textBlock.endIndex = indexOfReference + reference.length;

        return new TextResultBlock(textBlock, feedback);
    }

    private textBlockToTextResultBlock(feedback: Feedback): TextResultBlock | undefined {
        if (this.submission.blocks) {
            const result = this.submission.blocks.find((block) => block.id === feedback.reference);
            if (result) {
                const currentRating = this.feedbackRating.find((element) => element.feedback === feedback.id);
                let rating = 0;
                if (currentRating) {
                    rating = currentRating.rating;
                }
                return new TextResultBlock(result, feedback, rating);
            }
        }
    }

    public repeatForEachCredit(textResultBlock: TextResultBlock): number[] {
        if (!textResultBlock.feedback || textResultBlock.feedback.credits === 0) {
            return [];
        }

        const value = Math.ceil(Math.abs(textResultBlock.feedback.credits || 0));
        return new Array(value).fill(1);
    }

    public creditsTranslationForTextResultBlock(textResultBlock: TextResultBlock): string {
        const singular = Math.abs(textResultBlock.feedback!.credits || 0) === 1;

        return this.translateService.instant(`artemisApp.textAssessment.detail.credits.${singular ? 'one' : 'many'}`, { credits: textResultBlock.feedback!.credits });
    }

    onRate($event: { oldValue: number; newValue: number; starRating: StarRatingComponent }, block: TextResultBlock) {
        // update feedback locally
        block.setRating($event.newValue);

        // update feedback on the server
        this.textService.setRating(block.feedback!.id, $event.newValue);
    }
}
