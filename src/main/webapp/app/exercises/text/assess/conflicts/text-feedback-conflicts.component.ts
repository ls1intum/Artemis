import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Location } from '@angular/common';

import { TextAssessmentBaseComponent } from 'app/exercises/text/assess/text-assessment-base.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Result } from 'app/entities/result.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { AccountService } from 'app/core/auth/account.service';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';

import interact from 'interactjs';
import dayjs from 'dayjs/esm';
import { lastValueFrom } from 'rxjs';
import { faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-text-feedback-conflicts',
    templateUrl: './text-feedback-conflicts.component.html',
    styleUrls: ['./text-feedback-conflicts.component.scss'],
})
export class TextFeedbackConflictsComponent extends TextAssessmentBaseComponent implements OnInit, AfterViewInit {
    conflictingSubmissions?: TextSubmission[];
    leftSubmission?: TextSubmission;
    leftTextBlockRefs: TextBlockRef[];
    leftUnusedTextBlockRefs: TextBlockRef[];
    leftTotalScore: number;
    leftFeedbackId: number;
    rightSubmission?: TextSubmission;
    rightTextBlockRefs: TextBlockRef[];
    rightUnusedTextBlockRefs: TextBlockRef[];
    rightTotalScore: number;
    feedbackConflicts: FeedbackConflict[];
    overrideBusy = false;
    markBusy = false;
    isOverrideDisabled = true;
    isMarkingDisabled = true;
    selectedRightFeedbackId?: number;

    private get textBlocksWithFeedbackForLeftSubmission(): TextBlock[] {
        return [...this.leftTextBlockRefs, ...this.leftUnusedTextBlockRefs]
            .filter(({ block, feedback }) => block?.type === TextBlockType.AUTOMATIC || !!feedback)
            .map(({ block }) => block!);
    }

    // Icons
    faGripLinesVertical = faGripLinesVertical;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private location: Location,
        protected accountService: AccountService,
        protected assessmentsService: TextAssessmentService,
        protected alertService: AlertService,
        protected structuredGradingCriterionService: StructuredGradingCriterionService,
    ) {
        super(alertService, accountService, assessmentsService, structuredGradingCriterionService);
        const state = router.getCurrentNavigation()?.extras.state as { submission: TextSubmission };
        this.leftFeedbackId = Number(activatedRoute.snapshot.paramMap.get('feedbackId'));
        this.leftSubmission = state?.submission;
        this.exercise = this.leftSubmission?.participation?.exercise as TextExercise;
        this.leftTextBlockRefs = [];
        this.leftUnusedTextBlockRefs = [];
        this.rightTextBlockRefs = [];
        this.rightUnusedTextBlockRefs = [];
        this.feedbackConflicts = [];
    }

    /**
     *  Handles the resizable layout on the right-hand side. Adapted from:
     *  @see resizeable-container.component.ts
     */
    ngAfterViewInit() {
        interact('.movable')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 500, height: 0 },
                        max: { width: 750, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', (event: any) => {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', (event: any) => {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    async ngOnInit() {
        await super.ngOnInit();
        if (!this.leftSubmission) {
            const submissionId = Number(this.activatedRoute.snapshot.paramMap.get('submissionId'));
            const participationId = Number(this.activatedRoute.snapshot.paramMap.get('participationId'));
            const participation = await lastValueFrom(this.assessmentsService.getFeedbackDataForExerciseSubmission(participationId, submissionId));

            this.leftSubmission = participation!.submissions![0];
            setLatestSubmissionResult(this.leftSubmission, getLatestSubmissionResult(this.leftSubmission));
            this.exercise = participation!.exercise as TextExercise;
        }
        this.activatedRoute.data.subscribe(({ conflictingTextSubmissions }) => this.setPropertiesFromServerResponse(conflictingTextSubmissions));
    }

    private setPropertiesFromServerResponse(conflictingTextSubmissions: TextSubmission[]) {
        if (!this.leftSubmission) {
            return;
        }

        this.conflictingSubmissions = conflictingTextSubmissions;
        conflictingTextSubmissions.forEach((submission) => setLatestSubmissionResult(submission, getLatestSubmissionResult(submission)));
        this.prepareTextBlocksAndFeedbackFor(this.leftSubmission!, this.leftTextBlockRefs, this.leftUnusedTextBlockRefs);
        this.leftTotalScore = this.computeTotalScore(this.leftSubmission!.latestResult?.feedbacks!);
        this.setConflictingSubmission(0);
    }

    private setConflictingSubmission(index: number) {
        this.rightSubmission = this.conflictingSubmissions ? this.conflictingSubmissions[index] : undefined;
        if (this.rightSubmission) {
            this.prepareTextBlocksAndFeedbackFor(this.rightSubmission!, this.rightTextBlockRefs, this.rightUnusedTextBlockRefs);
            this.rightTotalScore = this.computeTotalScore(getLatestSubmissionResult(this.rightSubmission)!.feedbacks!);
            this.feedbackConflicts = getLatestSubmissionResult(this.leftSubmission)!.feedbacks!.find((f) => f.id === this.leftFeedbackId)?.conflictingTextAssessments || [];
        }
    }

    /**
     * Changes the displayed submission in the right text assessment area.
     * @param conflictIndex
     */
    didChangeConflictIndex(conflictIndex: number) {
        this.rightUnusedTextBlockRefs = [];
        this.rightTextBlockRefs = [];
        this.feedbackConflicts = [];
        this.selectedRightFeedbackId = undefined;
        this.isMarkingDisabled = true;
        this.setConflictingSubmission(conflictIndex - 1);
    }

    /**
     * Checks if the current user is the assessor of the passed result.
     * Passed result could be belong to left or right submission
     *
     * @param result - result to check its assessor
     */
    isAssessor(result: Result): boolean {
        return result?.assessor?.id === this.userId;
    }

    /**
     * Checks if the current user can override the submission.
     * Only possible if the user is an instructor for the exercise or
     * If s/he is an assessor of the submission and it is still before assessment due date.
     *
     * @param result - result to check override access
     */
    canOverride(result: Result): boolean {
        if (this.exercise) {
            if (this.exercise.isAtLeastInstructor) {
                // Instructors can override any assessment at any time.
                return true;
            }
            let isBeforeAssessmentDueDate = true;
            // Add check as the assessmentDueDate must not be set for exercises
            if (this.exercise.assessmentDueDate) {
                isBeforeAssessmentDueDate = dayjs().isBefore(this.exercise.assessmentDueDate!);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor(result) && isBeforeAssessmentDueDate;
        }
        return false;
    }

    /**
     * submits the left submission
     */
    overrideLeftSubmission() {
        if (!this.leftSubmission || !this.leftSubmission!.latestResult || !this.leftSubmission!.latestResult!.id || this.overrideBusy) {
            return;
        }

        this.overrideBusy = true;
        this.assessmentsService
            .submit(
                this.leftSubmission!.latestResult!.participation?.id!,
                this.leftSubmission!.latestResult!.id!,
                this.leftSubmission!.latestResult!.feedbacks!,
                this.textBlocksWithFeedbackForLeftSubmission,
            )
            .subscribe({
                next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
                error: (error: HttpErrorResponse) => this.handleError(error),
            });
    }

    /**
     * if the there is a change in left text block (one with the conflicts), total score is calculated again and
     * override button is enabled.
     */
    leftTextBlockRefsChange(): void {
        this.leftTotalScore = this.computeTotalScore(this.leftSubmission!.latestResult?.feedbacks!);
        this.isOverrideDisabled = false;
    }

    /**
     * selects and unselects one of the right conflicting feedback
     * @param rightFeedbackId - feedback id to un/select
     */
    didSelectConflictingFeedback(rightFeedbackId: number): void {
        this.selectedRightFeedbackId = rightFeedbackId !== this.selectedRightFeedbackId ? rightFeedbackId : undefined;
        this.isMarkingDisabled = !this.selectedRightFeedbackId;
    }

    /**
     * Finds the feedback conflict id based on the selected conflicting right feedback's id and calls the service function to solve conflict.
     */
    discardConflict(): void {
        if (this.markBusy || !this.selectedRightFeedbackId) {
            return;
        }

        const feedbackConflictId = this.feedbackConflicts.find((feedbackConflict) => feedbackConflict.conflictingFeedbackId === this.selectedRightFeedbackId)?.id;

        if (!feedbackConflictId || !this.exercise) {
            return;
        }

        this.markBusy = true;
        this.assessmentsService.solveFeedbackConflict(this.exercise!.id!, feedbackConflictId).subscribe({
            next: (response) => this.handleSolveConflictsSuccessWithAlert(response, 'artemisApp.textAssessment.solveFeedbackConflictSuccessful'),
            error: (error) => this.handleSolveConflictsError(error),
        });
    }

    private prepareTextBlocksAndFeedbackFor(submission: TextSubmission, textBlockRefs: TextBlockRef[], unusedTextBlockRefs: TextBlockRef[]): void {
        const feedbackList = submission.latestResult?.feedbacks || [];
        const matchBlocksWithFeedbacks = TextAssessmentService.matchBlocksWithFeedbacks(submission?.blocks || [], feedbackList);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, textBlockRefs, unusedTextBlockRefs, submission);
    }

    protected handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        super.handleSaveOrSubmitSuccessWithAlert(response, translationKey);
        this.overrideBusy = false;
        this.isOverrideDisabled = true;
        this.location.back();
    }

    private handleSolveConflictsSuccessWithAlert(response: FeedbackConflict, translationKey: string): void {
        this.alertService.success(translationKey);
        this.markBusy = false;
        this.isMarkingDisabled = true;
        this.location.back();
    }

    protected handleError(error: HttpErrorResponse): void {
        super.handleError(error);
        this.overrideBusy = false;
        this.isOverrideDisabled = true;
    }

    private handleSolveConflictsError(error: HttpErrorResponse): void {
        super.handleError(error);
        this.markBusy = false;
        this.isMarkingDisabled = true;
    }

    didClickedButtonNoConflict() {
        this.location.back();
    }
}
