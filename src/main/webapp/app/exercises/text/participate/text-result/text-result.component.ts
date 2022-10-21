import { Component, Input } from '@angular/core';
import { buildFeedbackTextForReview, checkSubsequentFeedbackInAssessment, Feedback } from 'app/entities/feedback.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { TextResultBlock } from './text-result-block';
import { TranslateService } from '@ngx-translate/core';
import { TextBlock } from 'app/entities/text-block.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-text-result',
    templateUrl: './text-result.component.html',
    styleUrls: ['./text-result.component.scss'],
})
export class TextResultComponent {
    public submissionText: string;

    public textResults: TextResultBlock[];
    private submission: TextSubmission;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    private readonly sha1Regex = /^[a-f0-9]{40}$/i;

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

    constructor(private translateService: TranslateService, private localeConversionService: LocaleConversionService) {}

    private convertTextToResultBlocks(feedbacks: Feedback[] = []): void {
        checkSubsequentFeedbackInAssessment(feedbacks);

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
                return new TextResultBlock(result, feedback);
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
        return this.translateService.instant(`artemisApp.assessment.detail.points.${singular ? 'one' : 'many'}`, {
            points: this.localeConversionService.toLocaleString(textResultBlock.feedback?.credits || 0, this.course?.accuracyOfScores),
        });
    }
}
