import { Injectable } from '@angular/core';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/result-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { computeFeedbackPreviewText } from 'app/exercises/shared/feedback/feedback.util';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';

@Injectable({ providedIn: 'root' })
export class FeedbackService {
    // TODO: Set exercise.type dynamically
    showTestDetails = false;

    constructor(private translateService: TranslateService) {}

    /**
     * Creates a feedback item with a category, title and text for each feedback object.
     * @param feedbacks The list of feedback objects.
     * @param isProgrammingExercise TODO: find other way
     */
    public createFeedbackItems(feedbacks: Feedback[], isProgrammingExercise: boolean): FeedbackItem[] {
        if (isProgrammingExercise) {
            return feedbacks.map((feedback) => this.createProgrammingExerciseFeedbackItem(feedback));
        } else {
            return feedbacks.map((feedback) => ({
                type: FeedbackItemType.Feedback,
                category: this.translateService.instant('artemisApp.result.detail.feedback'),
                title: feedback.text,
                text: feedback.detailText,
                previewText: computeFeedbackPreviewText(feedback.detailText),
                positive: feedback.positive,
                credits: feedback.credits,
            }));
        }
    }

    /**
     * Creates a feedback item for feedback received for a programming exercise.
     * @param feedback The feedback from which the feedback item should be created.
     * @private
     */
    private createProgrammingExerciseFeedbackItem(feedback: Feedback): FeedbackItem {
        const previewText = computeFeedbackPreviewText(feedback.detailText);

        if (Feedback.isSubmissionPolicyFeedback(feedback)) {
            return this.createProgrammingExerciseSubmissionPolicyFeedbackItem(feedback, previewText);
        } else if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
            return this.createProgrammingExerciseScaFeedbackItem(feedback);
        } else if (feedback.type === FeedbackType.AUTOMATIC) {
            return this.createProgrammingExerciseAutomaticFeedbackItem(feedback, previewText);
        } else if ((feedback.type === FeedbackType.MANUAL || feedback.type === FeedbackType.MANUAL_UNREFERENCED) && feedback.gradingInstruction) {
            return this.createProgrammingExerciseGradingInstructionFeedbackItem(feedback, previewText);
        } else {
            return this.createProgrammingExerciseTutorFeedbackItem(feedback, previewText);
        }
    }

    /**
     * Creates a feedback item from a submission policy feedback.
     * @param feedback The submission policy feedback.
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createProgrammingExerciseSubmissionPolicyFeedbackItem(feedback: Feedback, previewText?: string): FeedbackItem {
        const submissionPolicyTitle = feedback.text!.substring(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER.length);

        return {
            type: FeedbackItemType.Policy,
            category: this.translateService.instant('artemisApp.programmingExercise.submissionPolicy.title'),
            title: submissionPolicyTitle,
            text: feedback.detailText,
            previewText,
            positive: false,
            credits: feedback.credits,
        };
    }

    /**
     * Creates a feedback item from a feedback generated from static code analysis.
     * @param feedback A static code analysis feedback.
     * @private
     */
    private createProgrammingExerciseScaFeedbackItem(feedback: Feedback): FeedbackItem {
        const scaCategory = feedback.text!.substring(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length);
        const scaIssue = StaticCodeAnalysisIssue.fromFeedback(feedback);
        const text = this.showTestDetails ? `${scaIssue.rule}: ${scaIssue.message}` : scaIssue.message;
        const previewText = computeFeedbackPreviewText(text);

        return {
            type: FeedbackItemType.Issue,
            category: this.translateService.instant('artemisApp.result.detail.codeIssue.name'),
            title: this.translateService.instant('artemisApp.result.detail.codeIssue.title', { scaCategory, location: this.getIssueLocation(scaIssue) }),
            text,
            previewText,
            positive: false,
            credits: scaIssue.penalty ? -scaIssue.penalty : feedback.credits,
            actualCredits: feedback.credits,
        };
    }

    /**
     * Creates a feedback item from a feedback generated from an automatic test case result.
     * @param feedback A feedback received from an automatic test case.
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createProgrammingExerciseAutomaticFeedbackItem(feedback: Feedback, previewText?: string): FeedbackItem {
        let title = undefined;
        if (this.showTestDetails) {
            if (feedback.positive === undefined) {
                title = this.translateService.instant('artemisApp.result.detail.test.noInfo', { name: feedback.text });
            } else {
                title = feedback.positive
                    ? this.translateService.instant('artemisApp.result.detail.test.passed', { name: feedback.text })
                    : this.translateService.instant('artemisApp.result.detail.test.failed', { name: feedback.text });
            }
        }

        return {
            type: FeedbackItemType.Test,
            category: this.showTestDetails
                ? this.translateService.instant('artemisApp.result.detail.test.name')
                : this.translateService.instant('artemisApp.result.detail.feedback'),
            title,
            text: feedback.detailText,
            previewText,
            positive: feedback.positive,
            credits: feedback.credits,
        };
    }

    /**
     * Creates a feedback item for a manual feedback where the tutor used a grading instruction.
     * @param feedback The manual feedback where a grading instruction was used.
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createProgrammingExerciseGradingInstructionFeedbackItem(feedback: Feedback, previewText?: string): FeedbackItem {
        const gradingInstruction = feedback.gradingInstruction!;

        return {
            type: feedback.isSubsequent ? FeedbackItemType.Subsequent : FeedbackItemType.Feedback,
            category: this.showTestDetails ? this.translateService.instant('artemisApp.course.tutor') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: gradingInstruction.feedback + (feedback.detailText ? `\n${feedback.detailText}` : ''),
            previewText,
            positive: feedback.positive,
            credits: feedback.credits,
        };
    }

    /**
     * Creates a feedback item for a regular tutor feedback not using a grading instruction.
     * @param feedback The manual feedback from which the feedback item should be created.
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createProgrammingExerciseTutorFeedbackItem(feedback: Feedback, previewText?: string): FeedbackItem {
        return {
            type: FeedbackItemType.Feedback,
            category: this.showTestDetails ? this.translateService.instant('artemisApp.course.tutor') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: feedback.detailText,
            previewText,
            positive: feedback.positive,
            credits: feedback.credits,
        };
    }

    /**
     * Builds the location string for a static code analysis issue
     * @param issue The sca issue
     */
    private getIssueLocation(issue: StaticCodeAnalysisIssue): string {
        const lineText =
            !issue.endLine || issue.startLine === issue.endLine
                ? this.translateService.instant('artemisApp.result.detail.codeIssue.line', { line: issue.startLine })
                : this.translateService.instant('artemisApp.result.detail.codeIssue.lines', { from: issue.startLine, to: issue.endLine });
        if (issue.startColumn) {
            const columnText =
                !issue.endColumn || issue.startColumn === issue.endColumn
                    ? this.translateService.instant('artemisApp.result.detail.codeIssue.column', { line: issue.startColumn })
                    : this.translateService.instant('artemisApp.result.detail.codeIssue.columns', { from: issue.startColumn, to: issue.endColumn });
            return `${issue.filePath} ${lineText} ${columnText}`;
        }
        return `${issue.filePath} ${lineText}`;
    }
}
