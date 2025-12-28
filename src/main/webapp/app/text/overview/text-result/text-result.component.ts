import { Component, effect, inject, input } from '@angular/core';
import { Feedback, buildFeedbackTextForReview, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextResultBlock } from './text-result-block';
import { TranslateService } from '@ngx-translate/core';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-text-result',
    templateUrl: './text-result.component.html',
    styleUrls: ['./text-result.component.scss'],
    imports: [NgClass, FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class TextResultComponent {
    private translateService = inject(TranslateService);
    private localeConversionService = inject(LocaleConversionService);

    public submissionText: string;

    public textResults: TextResultBlock[];
    private submission: TextSubmission;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    private readonly SHA1_REGEX = /^[a-f0-9]{40}$/i;

    result = input<Result>();
    course = input<Course>();

    constructor() {
        // Effect to process result when it changes
        effect(() => {
            const resultValue = this.result();
            if (!resultValue || !resultValue.submission || !(resultValue.submission as TextSubmission)) {
                return;
            }

            this.submission = resultValue.submission as TextSubmission;
            this.submissionText = this.submission.text || '';
            this.convertTextToResultBlocks(resultValue.feedbacks);
        });
    }

    private convertTextToResultBlocks(feedbacks: Feedback[] = []): void {
        checkSubsequentFeedbackInAssessment(feedbacks);

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
            points: this.localeConversionService.toLocaleString(textResultBlock.feedback?.credits || 0, this.course()?.accuracyOfScores),
        });
    }
}
