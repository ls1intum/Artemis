import { FeedbackItemService } from 'app/exercises/shared/feedback/item/feedback-item-service';
import { Injectable, inject } from '@angular/core';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
    FeedbackType,
    NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER,
    STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER,
    SUBMISSION_POLICY_FEEDBACK_IDENTIFIER,
} from 'app/entities/feedback.model';
import { TranslateService } from '@ngx-translate/core';
import { StaticCodeAnalysisIssue } from 'app/entities/programming/static-code-analysis-issue.model';
import { getAllFeedbackGroups } from 'app/exercises/shared/feedback/group/programming-feedback-groups';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { Exercise } from 'app/entities/exercise.model';
import { FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';
import { FeedbackGroup } from 'app/exercises/shared/feedback/group/feedback-group';

@Injectable({ providedIn: 'root' })
export class ProgrammingFeedbackItemService implements FeedbackItemService {
    private translateService = inject(TranslateService);

    create(feedbacks: Feedback[], showTestDetails: boolean): FeedbackItem[] {
        return feedbacks.map((feedback) => this.createFeedbackItem(feedback, showTestDetails));
    }

    group(feedbackItems: FeedbackItem[], exercise: Exercise): FeedbackNode[] {
        const feedbackGroups = getAllFeedbackGroups(exercise)
            .map((group: FeedbackGroup) => group.addAllItems(feedbackItems.filter(group.shouldContain)))
            .filter((group: FeedbackGroup) => !group.isEmpty());

        if (feedbackGroups.length === 1) {
            feedbackGroups[0].open = true;
        }

        return feedbackGroups;
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
        } else if (Feedback.isFeedbackSuggestion(feedback)) {
            return this.createFeedbackSuggestionItem(feedback, showTestDetails);
        } else if (feedback.type === FeedbackType.AUTOMATIC && !Feedback.isNonGradedFeedbackSuggestion(feedback)) {
            return this.createAutomaticFeedbackItem(feedback, showTestDetails);
        } else if (feedback.type === FeedbackType.AUTOMATIC && Feedback.isNonGradedFeedbackSuggestion(feedback)) {
            return this.createNonGradedFeedbackItem(feedback);
        } else if ((feedback.type === FeedbackType.MANUAL || feedback.type === FeedbackType.MANUAL_UNREFERENCED) && feedback.gradingInstruction) {
            return this.createGradingInstructionFeedbackItem(feedback, showTestDetails);
        } else {
            return this.createReviewerFeedbackItem(feedback, showTestDetails);
        }
    }

    /**
     * Creates a feedback item from a submission policy feedback.
     * @param feedback The submission policy feedback.
     */
    private createSubmissionPolicyFeedbackItem(feedback: Feedback): FeedbackItem {
        const submissionPolicyTitle = feedback.text!.substring(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER.length);

        return {
            color: 'primary',
            type: 'Submission Policy',
            name: this.translateService.instant('artemisApp.programmingExercise.submissionPolicy.title'),
            title: submissionPolicyTitle,
            text: feedback.detailText,
            positive: false,
            credits: feedback.credits,
            feedbackReference: feedback,
        };
    }

    /**
     * Creates a feedback item from a feedback generated from static code analysis.
     * @param feedback A static code analysis feedback.
     * @param showTestDetails
     */
    private createScaFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        const scaCategory = feedback.text!.substring(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length);
        const scaIssue = StaticCodeAnalysisIssue.fromFeedback(feedback);
        const text = showTestDetails ? `${scaIssue.rule}: ${scaIssue.message}` : scaIssue.message;

        return {
            type: 'Static Code Analysis',
            name: this.translateService.instant('artemisApp.result.detail.codeIssue.name'),
            title: this.translateService.instant('artemisApp.result.detail.codeIssue.title', {
                scaCategory,
                location: this.getIssueLocation(scaIssue),
            }),
            text,
            positive: false,
            credits: scaIssue.penalty ? -scaIssue.penalty : feedback.credits,
            feedbackReference: feedback,
        };
    }

    /**
     * Creates a feedback item from a feedback suggestion.
     * @param feedback The feedback suggestion.
     * @param showTestDetails
     */
    private createFeedbackSuggestionItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        // A feedback suggestion should look like a manual feedback
        let titleWithoutIdentifier = feedback.text ?? '';
        // Remove prefix if it exists
        for (const prefix of [FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER]) {
            if (titleWithoutIdentifier.startsWith(prefix)) {
                titleWithoutIdentifier = titleWithoutIdentifier.substring(prefix.length);
                break;
            }
        }
        return {
            type: 'Reviewer', // Treat it like normal feedback from the TA
            name: showTestDetails ? this.translateService.instant('artemisApp.course.reviewer') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title: titleWithoutIdentifier,
            text: feedback.detailText,
            positive: feedback.positive,
            credits: feedback.credits,
            feedbackReference: feedback,
        };
    }

    /**
     * Creates a feedback item from a feedback generated from an automatic test case result.
     * @param feedback A feedback received from an automatic test case.
     * @param showTestDetails
     */
    private createAutomaticFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        let title = undefined;
        if (showTestDetails && feedback.testCase?.testName) {
            if (feedback.positive === undefined) {
                title = this.translateService.instant('artemisApp.result.detail.test.noInfo', { name: feedback.testCase.testName });
            } else {
                title = feedback.positive
                    ? this.translateService.instant('artemisApp.result.detail.test.passed', { name: feedback.testCase.testName })
                    : this.translateService.instant('artemisApp.result.detail.test.failed', { name: feedback.testCase.testName });
            }
        }
        return {
            type: 'Test',
            name: this.translateService.instant('artemisApp.result.detail.test.name'),
            title,
            text: feedback.detailText,
            positive: feedback.positive,
            credits: feedback.credits,
            feedbackReference: feedback,
        };
    }

    private createNonGradedFeedbackItem(feedback: Feedback): FeedbackItem {
        return {
            type: 'Reviewer',
            name: this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text?.slice(NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER.length),
            text: feedback.detailText,
            positive: feedback.positive,
            credits: feedback.credits,
            feedbackReference: feedback,
        };
    }

    /**
     * Creates a feedback item for a manual feedback where the tutor used a grading instruction.
     * @param feedback The manual feedback where a grading instruction was used.
     * @param showTestDetails
     */
    private createGradingInstructionFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        const gradingInstruction = feedback.gradingInstruction!;

        return {
            type: feedback.isSubsequent ? 'Subsequent' : 'Reviewer',
            name: showTestDetails ? this.translateService.instant('artemisApp.course.reviewer') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: gradingInstruction.feedback + (feedback.detailText ? `\n${feedback.detailText}` : ''),
            positive: feedback.positive,
            credits: feedback.credits,
            feedbackReference: feedback,
        };
    }

    /**
     * Creates a feedback item for a regular reviewer feedback not using a grading instruction.
     * @param feedback The manual feedback from which the feedback item should be created.
     * @param showTestDetails
     */
    private createReviewerFeedbackItem(feedback: Feedback, showTestDetails: boolean): FeedbackItem {
        return {
            type: 'Reviewer',
            name: showTestDetails ? this.translateService.instant('artemisApp.course.reviewer') : this.translateService.instant('artemisApp.result.detail.feedback'),
            title: feedback.text,
            text: feedback.detailText,
            positive: feedback.positive,
            credits: feedback.credits,
            feedbackReference: feedback,
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
                : this.translateService.instant('artemisApp.result.detail.codeIssue.lines', {
                      from: issue.startLine,
                      to: issue.endLine,
                  });
        if (issue.startColumn) {
            const columnText =
                !issue.endColumn || issue.startColumn === issue.endColumn
                    ? this.translateService.instant('artemisApp.result.detail.codeIssue.column', { column: issue.startColumn })
                    : this.translateService.instant('artemisApp.result.detail.codeIssue.columns', {
                          from: issue.startColumn,
                          to: issue.endColumn,
                      });
            return `${issue.filePath} ${lineText} ${columnText}`;
        }
        return `${issue.filePath} ${lineText}`;
    }
}
