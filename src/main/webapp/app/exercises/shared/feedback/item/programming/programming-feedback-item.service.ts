import { FeedbackItemService } from 'app/exercises/shared/feedback/item/feedback-item-service';
import { Injectable } from '@angular/core';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { TranslateService } from '@ngx-translate/core';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';
import { getAllFeedbackItemGroups } from 'app/exercises/shared/feedback/item/programming/programming-feedback-item-groups';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';

@Injectable({ providedIn: 'root' })
export class ProgrammingFeedbackItemService implements FeedbackItemService {
    constructor(private translateService: TranslateService) {}

    create(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[] {
        return feedbacks.map((feedback) => this.createFeedbackItem(feedback, showTestDetails));
    }

    group(feedbackItems: FeedbackItem[]): FeedbackItemGroup[] {
        return getAllFeedbackItemGroups() //
            .map((group) =>
                group
                    .addAllItems(feedbackItems.filter(group.shouldContain)) //
                    .calculateCredits(),
            )
            .filter((group) => !group.isEmpty());
    }

    /**
     * Creates a feedback item for feedback received for a programming exercise.
     * @param feedback The feedback from which the feedback item should be created.
     * @param showTestDetails
     */
    private createFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        if (Feedback.isSubmissionPolicyFeedback(feedback)) {
            return this.createSubmissionPolicyFeedbackItem(feedback);
        } else if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
            return this.createScaFeedbackItem(feedback, showTestDetails);
        } else if (feedback.type === FeedbackType.AUTOMATIC) {
            return this.createAutomaticFeedbackItem(feedback, showTestDetails);
        } else if ((feedback.type === FeedbackType.MANUAL || feedback.type === FeedbackType.MANUAL_UNREFERENCED) && feedback.gradingInstruction) {
            return this.createGradingInstructionFeedbackItem(feedback, showTestDetails);
        } else {
            return this.createTutorFeedbackItem(feedback, showTestDetails);
        }
    }

    /**
     * Creates a feedback item from a submission policy feedback.
     * @param feedback The submission policy feedback.
     * @private
     */
    private createSubmissionPolicyFeedbackItem(feedback: Feedback): FeedbackItem {
        const submissionPolicyTitle = feedback.text!.substring(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER.length);

        return {
            name: 'Submission Policy',
            type: 'Submission Policy',
            category: this.translateService.instant('artemisApp.programmingExercise.submissionPolicy.title'),
            title: submissionPolicyTitle,
            text: feedback.detailText,
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

        return {
            name: 'Static Code Analysis',
            type: 'Static Code Analysis',
            category: this.translateService.instant('artemisApp.result.detail.codeIssue.name'),
            title: this.translateService.instant('artemisApp.result.detail.codeIssue.title', { scaCategory, location: this.getIssueLocation(scaIssue) }),
            text,
            positive: false,
            credits: scaIssue.penalty ? -scaIssue.penalty : feedback.credits,
        };
    }

    /**
     * Creates a feedback item from a feedback generated from an automatic test case result.
     * @param feedback A feedback received from an automatic test case.
     * @param showTestDetails
     * @private
     */
    private createAutomaticFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
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
            name: 'Test',
            type: 'Test',
            category: showTestDetails ? this.translateService.instant('artemisApp.result.detail.test.name') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title,
            text: feedback.detailText,
            positive: feedback.positive,
            credits: feedback.credits,
        };
    }

    /**
     * Creates a feedback item for a manual feedback where the tutor used a grading instruction.
     * @param feedback The manual feedback where a grading instruction was used.
     * @param showTestDetails
     * @private
     */
    private createGradingInstructionFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        const gradingInstruction = feedback.gradingInstruction!;

        return {
            name: feedback.isSubsequent ? 'Subsequent' : 'Feedback',
            type: feedback.isSubsequent ? 'Subsequent' : 'Feedback',
            category: showTestDetails ? this.translateService.instant('artemisApp.course.tutor') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: gradingInstruction.feedback + (feedback.detailText ? `\n${feedback.detailText}` : ''),
            positive: feedback.positive,
            credits: feedback.credits,
        };
    }

    /**
     * Creates a feedback item for a regular tutor feedback not using a grading instruction.
     * @param feedback The manual feedback from which the feedback item should be created.
     * @param showTestDetails
     * @private
     */
    private createTutorFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        return {
            name: 'Feedback', // TODO: should be reviewer
            type: 'Feedback', // TODO: should be reviewer
            category: showTestDetails ? this.translateService.instant('artemisApp.course.tutor') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: feedback.detailText,
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
