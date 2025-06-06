import { Component, OnInit, inject } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextBlock, TextBlockType } from 'app/text/shared/entities/text-block.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { getPositiveAndCappedTotalScore, getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { captureException } from '@sentry/angular';

@Component({
    template: '',
})
export abstract class TextAssessmentBaseComponent implements OnInit {
    protected alertService = inject(AlertService);
    protected accountService = inject(AccountService);
    protected assessmentsService = inject(TextAssessmentService);
    protected structuredGradingCriterionService = inject(StructuredGradingCriterionService);

    /*
     * Base Component for TextSubmissionAssessmentComponent and ExampleTextSubmissionComponent since they share a lot of same functions.
     */

    exercise?: TextExercise;
    protected userId?: number;
    textBlockRefs: TextBlockRef[];
    unusedTextBlockRefs: TextBlockRef[];
    submission?: TextSubmission;

    readonly getCourseFromExercise = getCourseFromExercise;

    async ngOnInit() {
        // Used to check if the assessor is the current user
        const identity = await this.accountService.identity();
        this.userId = identity?.id;
    }

    protected computeTotalScore(assessments: Feedback[]): number {
        const maxPoints = getTotalMaxPoints(this.exercise);
        const totalScore = this.structuredGradingCriterionService.computeTotalScore(assessments);
        return getPositiveAndCappedTotalScore(totalScore, maxPoints);
    }

    protected handleSaveOrSubmitSuccessWithAlert(_response: HttpResponse<Result>, translationKey: string): void {
        this.alertService.success(translationKey);
    }

    protected handleError(error: HttpErrorResponse): void {
        const errorMessage = error.headers?.get('X-artemisApp-message') || error.message;
        this.alertService.error(errorMessage);
    }

    /**
     * Sorts text block refs by there appearance and checks for overlaps or gaps.
     * Prevent duplicate text when manual and automatic text blocks are present.
     *
     * @param matchBlocksWithFeedbacks
     * @param textBlockRefs
     * @param unusedTextBlockRefs
     * @param submission
     */
    protected sortAndSetTextBlockRefs(matchBlocksWithFeedbacks: TextBlockRef[], textBlockRefs: TextBlockRef[], unusedTextBlockRefs: TextBlockRef[], submission?: TextSubmission) {
        // Sort by start index to process all refs in order
        const sortedRefs = matchBlocksWithFeedbacks.sort((a, b) => a.block!.startIndex! - b.block!.startIndex!);

        let previousIndex = 0;
        const lastIndex = submission?.text?.length || 0;
        for (let i = 0; i <= sortedRefs.length; i++) {
            let ref: TextBlockRef | undefined = sortedRefs[i];
            const nextIndex = ref ? ref.block!.startIndex! : lastIndex;

            // last iteration, nextIndex = lastIndex. PreviousIndex > lastIndex is a sign for illegal state.
            if (!ref && previousIndex > nextIndex) {
                captureException('Illegal State: previous index cannot be greater than the last index!');

                // new text block starts before previous one ended (overlap)
            } else if (previousIndex > nextIndex) {
                const previousRef = textBlockRefs.pop();
                if (!previousRef) {
                    captureException('Overlapping Text Blocks with nothing? previousRef: ' + previousRef + ' ref: ' + ref);
                } else if ([ref, previousRef].every((r) => r.block?.type === TextBlockType.AUTOMATIC)) {
                    captureException('Overlapping AUTOMATIC Text Blocks! previousRef: ' + previousRef + ' ref: ' + ref);
                } else if ([ref, previousRef].every((r) => r.block?.type === TextBlockType.MANUAL)) {
                    // Make sure to select a TextBlockRef that has a feedback.
                    let selectedRef = ref;
                    if (!selectedRef.feedback) {
                        selectedRef = previousRef;
                    }

                    // Non-overlapping part of previousRef and ref should be added as a new text block (otherwise, some text is lost)
                    // But before, make sure that the selectedRef does not already cover the exact same range (otherwise, duplicate text blocks will appear)
                    if (selectedRef.block!.startIndex != previousRef.block!.startIndex! && selectedRef.block!.endIndex != nextIndex) {
                        TextAssessmentBaseComponent.addTextBlockByIndices(previousRef.block!.startIndex!, nextIndex, submission!, textBlockRefs);
                    }

                    ref = selectedRef;
                } else {
                    // Find which block is Manual and only keep that one. Automatic block is stored in `unusedTextBlockRefs` in case we need to restore.
                    switch (TextBlockType.MANUAL) {
                        case previousRef.block!.type:
                            unusedTextBlockRefs.push(ref);
                            ref = previousRef;
                            break;
                        case ref.block!.type:
                            unusedTextBlockRefs.push(previousRef);
                            TextAssessmentBaseComponent.addTextBlockByIndices(previousRef.block!.startIndex!, nextIndex, submission!, textBlockRefs);
                            break;
                    }
                }

                // If there is a gap between the current and previous block (most likely whitespace or linebreak), we need to create a new text block as well.
            } else if (previousIndex < nextIndex) {
                // There is a gap. We need to add a Text Block in between
                TextAssessmentBaseComponent.addTextBlockByIndices(previousIndex, nextIndex, submission!, textBlockRefs);
                previousIndex = nextIndex;
            }

            if (ref) {
                textBlockRefs.push(ref);
                previousIndex = ref.block!.endIndex!;
            }
        }
    }

    private static addTextBlockByIndices(startIndex: number, endIndex: number, submission: TextSubmission, textBlockRefs: TextBlockRef[]): void {
        if (startIndex >= endIndex) {
            return;
        }

        const newRef = TextBlockRef.new();
        if (newRef.block) {
            newRef.block.startIndex = startIndex;
            newRef.block.endIndex = endIndex;
            newRef.block.setTextFromSubmission(submission!);
        }
        textBlockRefs.push(newRef);
    }

    /**
     * Invoked by Child @Output when adding/removing text blocks. Recalculating refs to keep order and prevent duplicate text displayed.
     */
    public recalculateTextBlockRefs(): void {
        // This is racing with another @Output, so we wait one loop
        setTimeout(() => {
            const refs = [...this.textBlockRefs, ...this.unusedTextBlockRefs].filter(({ block, feedback }) => block!.type === TextBlockType.AUTOMATIC || !!feedback);
            this.textBlockRefs = [];
            this.unusedTextBlockRefs = [];

            this.sortAndSetTextBlockRefs(refs, this.textBlockRefs, this.unusedTextBlockRefs, this.submission);
        });
    }

    protected get textBlocksWithFeedback(): TextBlock[] {
        return [...this.textBlockRefs, ...this.unusedTextBlockRefs]
            .filter(({ block, feedback }) => block?.type === TextBlockType.AUTOMATIC || !!feedback)
            .map(({ block }) => block!);
    }
}
