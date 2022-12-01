import { FeedbackItemService } from 'app/exercises/shared/feedback/feedback-item-service';
import { Injectable } from '@angular/core';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/result-detail.component';
import { computeFeedbackPreviewText } from 'app/exercises/shared/feedback/feedback.util';
import { TranslateService } from '@ngx-translate/core';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';
import { FeedbackItemGroup, getAllFeedbackItemGroups } from 'app/exercises/shared/feedback/programming-feedback-item-groups';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseFeedbackItemService implements FeedbackItemService {
    constructor(private translateService: TranslateService) {}

    getPositiveTestCasesWithoutDetailText(feedbackItems: FeedbackItem[]): FeedbackItem[] {
        return feedbackItems.filter((feedbackItem) => {
            return feedbackItem.type === FeedbackItemType.Test && feedbackItem.positive && !feedbackItem.text;
        });
    }

    createFeedbackItems(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[] {
        return feedbacks.map((feedback) => this.createFeedbackItem(feedback, showTestDetails));
    }

    groupFeedbackItems(feedbackItems: FeedbackItem[]): FeedbackItemGroup[] {
        return getAllFeedbackItemGroups()
            .map((group) => group.addAll(feedbackItems.filter(group.shouldContain)))
            .filter((group) => group.isEmpty());
    }

    filterFeedbackItems(feedbackItems: FeedbackItem[], showTestDetails: boolean): FeedbackItem[] {
        if (showTestDetails) {
            return [...feedbackItems];
        }

        const positiveTestCasesWithoutDetailText = this.getPositiveTestCasesWithoutDetailText(feedbackItems);

        if (positiveTestCasesWithoutDetailText.length > 0) {
            // Add summary of positive test cases
            return [
                {
                    type: FeedbackItemType.Test,
                    category: showTestDetails
                        ? this.translateService.instant('artemisApp.result.detail.test.name')
                        : this.translateService.instant('artemisApp.result.detail.feedback'),
                    title:
                        positiveTestCasesWithoutDetailText.length > 1
                            ? this.translateService.instant('artemisApp.result.detail.test.passedTests', { number: positiveTestCasesWithoutDetailText.length })
                            : this.translateService.instant('artemisApp.result.detail.test.passedTest'),
                    positive: true,
                    credits: positiveTestCasesWithoutDetailText.reduce((sum, feedbackItem) => sum + (feedbackItem.credits ?? 0), 0),
                },
                ...feedbackItems.filter((feedbackItem) => !positiveTestCasesWithoutDetailText.includes(feedbackItem)),
            ];
        }

        return [...feedbackItems];
    }

    /**
     * Creates a feedback item for feedback received for a programming exercise.
     * @param feedback The feedback from which the feedback item should be created.
     * @param showTestDetails
     */
    private createFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        const previewText = computeFeedbackPreviewText(feedback.detailText);

        if (Feedback.isSubmissionPolicyFeedback(feedback)) {
            return this.createSubmissionPolicyFeedbackItem(feedback, previewText);
        } else if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
            return this.createScaFeedbackItem(feedback, showTestDetails);
        } else if (feedback.type === FeedbackType.AUTOMATIC) {
            return this.createAutomaticFeedbackItem(feedback, showTestDetails, previewText);
        } else if ((feedback.type === FeedbackType.MANUAL || feedback.type === FeedbackType.MANUAL_UNREFERENCED) && feedback.gradingInstruction) {
            return this.createGradingInstructionFeedbackItem(feedback, showTestDetails, previewText);
        } else {
            return this.createTutorFeedbackItem(feedback, showTestDetails, previewText);
        }
    }

    /**
     * Creates a feedback item from a submission policy feedback.
     * @param feedback The submission policy feedback.
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createSubmissionPolicyFeedbackItem(feedback: Feedback, previewText?: string): FeedbackItem {
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
     * @param showTestDetails
     * @private
     */
    private createScaFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        const scaCategory = feedback.text!.substring(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length);
        const scaIssue = StaticCodeAnalysisIssue.fromFeedback(feedback);
        const text = showTestDetails ? `${scaIssue.rule}: ${scaIssue.message}` : scaIssue.message;
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
     * @param showTestDetails
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createAutomaticFeedbackItem(feedback: Feedback, showTestDetails: boolean, previewText?: string): FeedbackItem {
        let title = undefined;
        if (showTestDetails) {
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
            category: showTestDetails ? this.translateService.instant('artemisApp.result.detail.test.name') : this.translateService.instant('artemisApp.result.detail.feedback'),
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
     * @param showTestDetails
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createGradingInstructionFeedbackItem(feedback: Feedback, showTestDetails: boolean, previewText?: string): FeedbackItem {
        const gradingInstruction = feedback.gradingInstruction!;

        return {
            type: feedback.isSubsequent ? FeedbackItemType.Subsequent : FeedbackItemType.Feedback,
            category: showTestDetails ? this.translateService.instant('artemisApp.course.tutor') : this.translateService.instant('artemisApp.result.detail.feedback'),
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
     * @param showTestDetails
     * @param previewText The preview text for the feedback item.
     * @private
     */
    private createTutorFeedbackItem(feedback: Feedback, showTestDetails: boolean, previewText?: string): FeedbackItem {
        return {
            type: FeedbackItemType.Feedback,
            category: showTestDetails ? this.translateService.instant('artemisApp.course.tutor') : this.translateService.instant('artemisApp.result.detail.feedback'),
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
