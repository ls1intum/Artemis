import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import * as moment from 'moment';

import { AccountService } from 'app/core/auth/account.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Result } from 'app/entities/result.model';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { Feedback } from 'app/entities/feedback.model';
import { notUndefined } from 'app/shared/util/global.utils';
import { TextBlock } from 'app/entities/text-block.model';
import { TranslateService } from '@ngx-translate/core';
import { NEW_ASSESSMENT_PATH } from 'app/exercises/text/assess-new/text-submission-assessment.route';

@Component({
    selector: 'jhi-text-submission-assessment',
    templateUrl: './text-submission-assessment.component.html',
    styleUrls: ['./text-submission-assessment.component.scss'],
})
export class TextSubmissionAssessmentComponent implements OnInit {
    private userId: number | null;
    participation: StudentParticipation | null = null;
    submission: TextSubmission | null = null;
    exercise: TextExercise | null = null;
    result: Result | null = null;
    generalFeedback: Feedback;
    textBlockRefs: TextBlockRef[] = [];
    totalScore = 0;

    isLoading = true;
    busy = false;
    isAssessor = false;
    isAtLeastInstructor = false;
    canOverride = false;
    assessmentsAreValid = false;
    complaint: Complaint;
    noNewSubmissions = false;

    private cancelConfirmationText: string;

    private get referencedFeedback(): Feedback[] {
        return this.textBlockRefs.map(({ feedback }) => feedback).filter(notUndefined) as Feedback[];
    }

    private get assessments(): Feedback[] {
        if (Feedback.hasDetailText(this.generalFeedback)) {
            return [this.generalFeedback, ...this.referencedFeedback];
        } else {
            return this.referencedFeedback;
        }
    }

    private get textBlocks(): TextBlock[] {
        return this.textBlockRefs.map(({ block }) => block);
    }

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private location: Location,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private assessmentsService: TextAssessmentsService,
        private complaintService: ComplaintService,
        translateService: TranslateService,
    ) {
        translateService.get('artemisApp.textAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    async ngOnInit() {
        // Used to check if the assessor is the current user
        const identity = await this.accountService.identity();
        this.userId = identity ? identity.id : null;

        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);

        this.activatedRoute.data.subscribe(({ studentParticipation }) => this.setPropertiesFromServerResponse(studentParticipation));
    }

    private setPropertiesFromServerResponse(studentParticipation: StudentParticipation) {
        // Update noNewSubmissions
        this.noNewSubmissions = this.isNewAssessmentRoute ? studentParticipation === null : false;

        this.participation = studentParticipation;
        this.submission = this.participation?.submissions[0] as TextSubmission;
        this.exercise = this.participation?.exercise as TextExercise;
        this.result = this.submission?.result;
        this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise!.course!);
        this.prepareTextBlocksAndFeedbacks();
        this.getComplaint();
        this.updateUrlIfNeeded();

        this.checkPermissions();
        this.computeTotalScore();
        this.isLoading = false;
    }

    private updateUrlIfNeeded() {
        if (this.isNewAssessmentRoute) {
            // Update the url with the new id, without reloading the page, to make the history consistent
            const newUrl = this.router
                .createUrlTree(['course-management', this.exercise?.course?.id, 'text-exercises', this.exercise?.id, 'submissions', this.submission?.id, 'assessment'])
                .toString();
            this.location.go(newUrl);
        }
    }

    private get isNewAssessmentRoute(): boolean {
        return this.activatedRoute.routeConfig?.path === NEW_ASSESSMENT_PATH;
    }

    navigateBack(): void {
        history.back();
    }

    save(): void {
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.busy = true;
        this.assessmentsService.save(this.exercise!.id, this.result!.id, this.assessments, this.textBlocks).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.saveSuccessful'),
            (error: HttpErrorResponse) => this.handleError(error),
        );
    }

    submit(): void {
        if (!this.result?.id) {
            return; // We need to have saved the result before
        }

        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.busy = true;
        this.assessmentsService.submit(this.exercise!.id, this.result!.id, this.assessments, this.textBlocks).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
            (error: HttpErrorResponse) => this.handleError(error),
        );
    }

    private handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        this.participation!.results[0] = this.result = response.body!;
        this.jhiAlertService.success(translationKey);
        this.busy = false;
    }

    cancel(): void {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        this.busy = true;
        if (confirmCancel && this.exercise && this.submission) {
            this.assessmentsService
                .cancelAssessment(this.exercise.id, this.submission.id)
                .subscribe(() => this.router.navigate(['course-management', this.exercise?.course?.id, 'exercises', this.exercise?.id, 'tutor-dashboard']));
        }
    }

    nextSubmission(): void {
        this.busy = true;
        this.router.navigate(['course-management', this.exercise?.course?.id, 'text-exercises', this.exercise?.id, 'submissions', 'new', 'assessment']);
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    updateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.assessmentsService.updateAssessmentAfterComplaint(this.assessments, this.textBlocks, complaintResponse, this.submission?.id!).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.updateAfterComplaintSuccessful'),
            (error: HttpErrorResponse) => {
                console.error(error);
                this.jhiAlertService.clear();
                this.jhiAlertService.error('artemisApp.textAssessment.updateAfterComplaintFailed');
                this.busy = false;
            },
        );
    }

    private computeTotalScore() {
        const credits = this.assessments.map((feedback) => feedback.credits);
        this.totalScore = credits.reduce((a, b) => a + b, 0);
    }

    validateFeedback(): void {
        const hasReferencedFeedback = this.referencedFeedback.filter((f) => !Feedback.isEmpty(f)).length > 0;
        const hasGeneralFeedback = Feedback.hasDetailText(this.generalFeedback);

        this.assessmentsAreValid = hasReferencedFeedback || hasGeneralFeedback;

        this.computeTotalScore();
    }

    private prepareTextBlocksAndFeedbacks(): void {
        if (!this.result) {
            return;
        }
        const feedbacks = this.result.feedbacks || [];
        const generalFeedbackIndex = feedbacks.findIndex(({ reference }) => reference == null);
        if (generalFeedbackIndex !== -1) {
            this.generalFeedback = feedbacks[generalFeedbackIndex];
            feedbacks.splice(generalFeedbackIndex, 1);
        } else {
            this.generalFeedback = new Feedback();
        }

        const sortedRefs = TextAssessmentsService.matchBlocksWithFeedbacks(this.submission?.blocks || [], feedbacks).sort((a, b) => a.block.startIndex - b.block.startIndex);

        let previousIndex = 0;
        const lastIndex = this.submission?.text.length || 0;
        for (let i = 0; i <= sortedRefs.length; i++) {
            const ref: TextBlockRef | undefined = sortedRefs[i];
            const nextIndex = ref ? ref.block.startIndex : lastIndex;
            if (previousIndex > nextIndex) {
                console.error('Overlapping Text Blocks!', ref, previousIndex, nextIndex, sortedRefs);
            } else if (previousIndex < nextIndex) {
                // There is a gap. We need to add a Text Block in between
                this.addTextBlockByIndices(previousIndex, nextIndex);
                previousIndex = nextIndex;
            }

            if (ref) {
                this.textBlockRefs.push(ref);
                previousIndex = ref.block.endIndex;
            }
        }
    }

    private addTextBlockByIndices(startIndex: number, endIndex: number): void {
        const newRef = TextBlockRef.new();
        newRef.block.startIndex = startIndex;
        newRef.block.endIndex = endIndex;
        newRef.block.setTextFromSubmission(this.submission!);
        newRef.block.computeId();
        this.textBlockRefs.push(newRef);
    }

    private getComplaint(): void {
        if (!this.result?.hasComplaint) {
            return;
        }

        this.busy = true;
        this.complaintService.findByResultId(this.result.id).subscribe(
            (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
                this.busy = false;
            },
            (err: HttpErrorResponse) => {
                this.handleError(err.error);
            },
        );
    }

    private checkPermissions(): void {
        this.isAssessor = this.result !== null && this.result.assessor && this.result.assessor.id === this.userId;
        const isBeforeAssessmentDueDate = moment().isBefore(this.exercise?.assessmentDueDate!);
        // tutors are allowed to override one of their assessments before the assessment due date. instructors can override any assessment at any time.
        this.canOverride = (this.isAssessor && isBeforeAssessmentDueDate) || this.isAtLeastInstructor;
    }

    private handleError(error: HttpErrorResponse): void {
        const errorMessage = error.headers?.get('X-artemisApp-message') || error.message;
        this.jhiAlertService.error(errorMessage, null, undefined);
        this.busy = false;
    }
}
