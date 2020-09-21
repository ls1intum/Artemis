import { Component, OnInit, AfterViewInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';

import { TextSubmission } from 'app/entities/text-submission.model';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Feedback } from 'app/entities/feedback.model';
import { Result } from 'app/entities/result.model';
import { TextAssessmentConflict } from 'app/entities/text-assessment-conflict';
import { AccountService } from 'app/core/auth/account.service';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

import interact from 'interactjs';
import * as moment from 'moment';

@Component({
    selector: 'jhi-text-assessment-conflicts',
    templateUrl: './text-assessment-conflicts.component.html',
    styleUrls: ['./text-assessment-conflicts.component.scss'],
})
export class TextAssessmentConflictsComponent implements OnInit, AfterViewInit {
    exercise: TextExercise | null;
    conflictingSubmissions: TextSubmission[] | null;
    leftSubmission: TextSubmission | null;
    leftTextBlockRefs: TextBlockRef[];
    leftUnusedTextBlockRefs: TextBlockRef[];
    leftTotalScore: number;
    leftFeedbackId: number;
    rightSubmission: TextSubmission | null;
    rightTextBlockRefs: TextBlockRef[];
    rightUnusedTextBlockRefs: TextBlockRef[];
    rightTotalScore: number;
    rightFeedbackIds: number[] | undefined;
    conflictingAssessments: TextAssessmentConflict[];

    private userId: number | null;
    isAtLeastInstructor: boolean;
    overrideBusy = false;
    markBusy = false;
    isOverrideDisabled = true;
    isMarkingDisabled = true;
    selectedRightFeedbackId: number | null;

    private get textBlocksWithFeedbackForLeftSubmission(): TextBlock[] {
        return [...this.leftTextBlockRefs, ...this.leftUnusedTextBlockRefs]
            .filter(({ block, feedback }) => block.type === TextBlockType.AUTOMATIC || !!feedback)
            .map(({ block }) => block);
    }

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private assessmentsService: TextAssessmentsService,
        private jhiAlertService: JhiAlertService,
        public structuredGradingCriterionService: StructuredGradingCriterionService,
    ) {
        const state = router.getCurrentNavigation()?.extras.state as { submission: TextSubmission };
        this.leftFeedbackId = Number(activatedRoute.snapshot.queryParams['id']);
        this.leftSubmission = state.submission;
        this.exercise = this.leftSubmission.participation?.exercise as TextExercise;
        this.leftTextBlockRefs = [];
        this.leftUnusedTextBlockRefs = [];
        this.rightTextBlockRefs = [];
        this.rightUnusedTextBlockRefs = [];
        this.rightFeedbackIds = [];
        this.conflictingAssessments = [];
    }

    ngAfterViewInit() {
        interact('.movable')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 215, height: 0 },
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
        const identity = await this.accountService.identity();
        this.userId = identity ? identity.id : null;
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
        this.activatedRoute.data.subscribe(({ conflictingTextSubmissions }) => this.setPropertiesFromServerResponse(conflictingTextSubmissions));
    }

    didChangeConflictIndex(conflictIndex: number) {
        this.rightUnusedTextBlockRefs = [];
        this.rightTextBlockRefs = [];
        this.conflictingAssessments = [];
        this.selectedRightFeedbackId = null;
        this.setConflictingSubmission(conflictIndex - 1);
    }

    private setPropertiesFromServerResponse(conflictingTextSubmissions: TextSubmission[]) {
        this.conflictingSubmissions = conflictingTextSubmissions;
        this.prepareTextBlocksAndFeedbackFor(this.leftSubmission!, this.leftTextBlockRefs, this.leftUnusedTextBlockRefs);
        this.leftTotalScore = this.computeTotalScore(this.leftSubmission!.result.feedbacks);
        this.setConflictingSubmission(0);
    }

    private setConflictingSubmission(index: number) {
        this.rightSubmission = this.conflictingSubmissions ? this.conflictingSubmissions[index] : null;
        this.prepareTextBlocksAndFeedbackFor(this.rightSubmission!, this.rightTextBlockRefs, this.rightUnusedTextBlockRefs);
        this.rightTotalScore = this.computeTotalScore(this.rightSubmission!.result.feedbacks);
        this.conflictingAssessments = this.leftSubmission?.result.feedbacks.find((f) => f.id === this.leftFeedbackId)?.conflictingTextAssessments || [];
    }

    overrideLeftSubmission() {
        if (!this.leftSubmission || !this.leftSubmission.result || !this.leftSubmission.result.id || this.overrideBusy) {
            return;
        }

        this.overrideBusy = true;
        this.assessmentsService
            .submit(this.exercise!.id, this.leftSubmission!.result.id, this.leftSubmission!.result.feedbacks, this.textBlocksWithFeedbackForLeftSubmission)
            .subscribe(
                (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
                (error: HttpErrorResponse) => this.handleSubmitError(error),
            );
    }

    leftTextBlockRefsChange(): void {
        this.leftTotalScore = this.computeTotalScore(this.leftSubmission!.result.feedbacks);
        this.isOverrideDisabled = false;
    }

    didSelectConflictingFeedback(rightFeedbackId: number): void {
        this.selectedRightFeedbackId = rightFeedbackId !== this.selectedRightFeedbackId ? rightFeedbackId : null;
        this.isMarkingDisabled = !this.selectedRightFeedbackId;
    }

    markSelectedAsNoConflict(): void {
        if (this.markBusy || !this.selectedRightFeedbackId) {
            return;
        }

        const textAssessmentConflictId = this.conflictingAssessments.find((conflictingAssessment) => conflictingAssessment.conflictingFeedbackId === this.selectedRightFeedbackId)
            ?.id;

        if (!textAssessmentConflictId) {
            return;
        }

        this.markBusy = true;
        this.assessmentsService.setConflictsAsSolved(textAssessmentConflictId).subscribe(
            (response) => this.handleSolveConflictsSuccessWithAlert(response, ''),
            (error) => this.handleSolveConflictsError(error),
        );
    }

    isAssessor(result: Result): boolean {
        return result !== null && result.assessor && result.assessor.id === this.userId;
    }

    canOverride(result: Result): boolean {
        if (this.exercise) {
            if (this.isAtLeastInstructor) {
                // Instructors can override any assessment at any time.
                return true;
            }
            let isBeforeAssessmentDueDate = true;
            // Add check as the assessmentDueDate must not be set for exercises
            if (this.exercise.assessmentDueDate) {
                isBeforeAssessmentDueDate = moment().isBefore(this.exercise.assessmentDueDate!);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor(result) && isBeforeAssessmentDueDate;
        }
        return false;
    }

    private computeTotalScore(assessments: Feedback[]): number {
        return this.structuredGradingCriterionService.computeTotalScore(assessments);
    }

    private prepareTextBlocksAndFeedbackFor(submission: TextSubmission, textBlockRefs: TextBlockRef[], unusedTextBlockRefs: TextBlockRef[]): void {
        const feedbackList = submission.result.feedbacks || [];
        const matchBlocksWithFeedbacks = TextAssessmentsService.matchBlocksWithFeedbacks(submission?.blocks || [], feedbackList);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, textBlockRefs, unusedTextBlockRefs, submission);
    }

    private sortAndSetTextBlockRefs(matchBlocksWithFeedbacks: TextBlockRef[], textBlockRefs: TextBlockRef[], unusedTextBlockRefs: TextBlockRef[], submission: TextSubmission) {
        // Sort by start index to process all refs in order
        const sortedRefs = matchBlocksWithFeedbacks.sort((a, b) => a.block.startIndex - b.block.startIndex);

        let previousIndex = 0;
        const lastIndex = submission?.text?.length || 0;
        for (let i = 0; i <= sortedRefs.length; i++) {
            let ref: TextBlockRef | undefined = sortedRefs[i];
            const nextIndex = ref ? ref.block.startIndex : lastIndex;

            // last iteration, nextIndex = lastIndex. PreviousIndex > lastIndex is a sign for illegal state.
            if (!ref && previousIndex > nextIndex) {
                console.error('Illegal State: previous index cannot be greated than the last index!');

                // new text block starts before previous one ended (overlap)
            } else if (previousIndex > nextIndex) {
                const previousRef = textBlockRefs.pop();
                if (!previousRef) {
                    console.error('Overlapping Text Blocks with nothing?', previousRef, ref);
                } else if ([ref, previousRef].every((r) => r.block.type === TextBlockType.AUTOMATIC)) {
                    console.error('Overlapping AUTOMATIC Text Blocks!', previousRef, ref);
                } else if ([ref, previousRef].every((r) => r.block.type === TextBlockType.MANUAL)) {
                    console.error('Overlapping MANUAL Text Blocks!', previousRef, ref);
                } else {
                    // Find which block is Manual and only keep that one. Automatic block is stored in `unusedTextBlockRefs` in case we need to restore.
                    switch (TextBlockType.MANUAL) {
                        case previousRef.block.type:
                            unusedTextBlockRefs.push(ref);
                            ref = previousRef;
                            break;
                        case ref.block.type:
                            unusedTextBlockRefs.push(previousRef);
                            this.addTextBlockByIndices(previousRef.block.startIndex, nextIndex, submission, textBlockRefs);
                            break;
                    }
                }

                // If there is a gap between the current and previous block (most likely whitespace or linebreak), we need to create a new text block as well.
            } else if (previousIndex < nextIndex) {
                // There is a gap. We need to add a Text Block in between
                this.addTextBlockByIndices(previousIndex, nextIndex, submission, textBlockRefs);
                previousIndex = nextIndex;
            }

            if (ref) {
                textBlockRefs.push(ref);
                previousIndex = ref.block.endIndex;
            }
        }
    }

    private addTextBlockByIndices(startIndex: number, endIndex: number, submission: TextSubmission, textBlockRefs: TextBlockRef[]): void {
        if (startIndex >= endIndex) {
            return;
        }

        const newRef = TextBlockRef.new();
        newRef.block.startIndex = startIndex;
        newRef.block.endIndex = endIndex;
        newRef.block.setTextFromSubmission(submission!);
        newRef.block.computeId();
        textBlockRefs.push(newRef);
    }

    private handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        this.jhiAlertService.success(translationKey);
        this.overrideBusy = false;
        this.isOverrideDisabled = true;
    }

    private handleSolveConflictsSuccessWithAlert(response: TextAssessmentConflict, translationKey: string): void {
        this.jhiAlertService.success(translationKey);
        this.markBusy = false;
        this.isMarkingDisabled = true;
    }

    private handleSubmitError(error: HttpErrorResponse): void {
        const errorMessage = error.headers?.get('X-artemisApp-message') || error.message;
        this.jhiAlertService.error(errorMessage, null, undefined);
        this.overrideBusy = false;
        this.isOverrideDisabled = true;
    }

    private handleSolveConflictsError(error: HttpErrorResponse): void {
        const errorMessage = error.headers?.get('X-artemisApp-message') || error.message;
        this.jhiAlertService.error(errorMessage, null, undefined);
        this.markBusy = false;
        this.isMarkingDisabled = true;
    }
}
