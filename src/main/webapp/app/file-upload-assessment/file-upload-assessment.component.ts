/* angular */
import { Component, OnInit, ChangeDetectorRef, AfterViewInit, OnDestroy } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

/* 3rd party*/
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';
import interact from 'interactjs';
import * as moment from 'moment';
import { Interactable } from '@interactjs/types/types';
import { Location } from '@angular/common';

/* application */
import { FileUploadAssessmentsService } from 'app/entities/file-upload-assessment/file-upload-assessment.service';
import { AccountService, WindowRef } from 'app/core';
import { StudentParticipation } from 'app/entities/participation';
import { Result, ResultService } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { Complaint, ComplaintService, ComplaintType } from 'app/entities/complaint';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { FileUploadSubmission, FileUploadSubmissionService } from 'app/entities/file-upload-submission';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';
import { ExerciseType } from 'app/entities/exercise';
import { filter } from 'rxjs/operators';

@Component({
    providers: [FileUploadAssessmentsService, WindowRef],
    templateUrl: './file-upload-assessment.component.html',
})
export class FileUploadAssessmentComponent implements OnInit, AfterViewInit, OnDestroy {
    text: string;
    participation: StudentParticipation;
    submission: FileUploadSubmission;
    unassessedSubmission: FileUploadSubmission;
    result: Result;
    generalFeedback: Feedback;
    referencedFeedback: Feedback[];
    exercise: FileUploadExercise;
    totalScore = 0;
    assessmentsAreValid: boolean;
    invalidError: string | null;
    isAssessor = true;
    isAtLeastInstructor = false;
    busy = true;
    showResult = true;
    complaint: Complaint;
    ComplaintType = ComplaintType;
    notFound = false;
    userId: number;
    canOverride = false;

    formattedProblemStatement: SafeHtml | null;
    formattedSampleSolution: SafeHtml | null;
    formattedGradingInstructions: SafeHtml | null;

    /** Resizable constants **/
    resizableMinWidth = 100;
    resizableMaxWidth = 1200;
    resizableMinHeight = 200;
    interactResizable: Interactable;
    interactResizableTop: Interactable;

    private cancelConfirmationText: string;

    constructor(
        private changeDetectorRef: ChangeDetectorRef,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private resultService: ResultService,
        private fileUploadAssessmentsService: FileUploadAssessmentsService,
        private accountService: AccountService,
        private location: Location,
        private $window: WindowRef,
        private artemisMarkdown: ArtemisMarkdown,
        private translateService: TranslateService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private complaintService: ComplaintService,
    ) {
        this.generalFeedback = new Feedback();
        this.referencedFeedback = [];
        this.assessmentsAreValid = false;
        translateService.get('artemisApp.textAssessment.confirmCancel').subscribe(text => (this.cancelConfirmationText = text));
    }

    get assessments(): Feedback[] {
        return [this.generalFeedback, ...this.referencedFeedback];
    }

    public ngOnInit(): void {
        this.busy = true;

        // Used to check if the assessor is the current user
        this.accountService.identity().then(user => {
            this.userId = user!.id!;
        });
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);

        this.route.params.subscribe(params => {
            const exerciseId = Number(params['exerciseId']);
            const submissionValue = params['submissionId'];
            const submissionId = Number(submissionValue);
            if (submissionValue === 'new') {
                this.loadOptimalSubmission(exerciseId);
            } else {
                this.loadSubmission(submissionId);
            }
        });
    }

    attachmentExtension(filePath: string): string {
        if (!filePath) {
            return 'N/A';
        }

        return filePath.split('.').pop()!;
    }

    private loadOptimalSubmission(exerciseId: number): void {
        this.fileUploadSubmissionService.getFileUploadSubmissionForExerciseWithoutAssessment(exerciseId).subscribe(
            (submission: FileUploadSubmission) => {
                this.submission = submission;
                const studentParticipation = this.submission.participation as StudentParticipation;
                this.exercise = studentParticipation.exercise as FileUploadExercise;
                this.checkPermissions();
                this.busy = false;

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
                this.location.go(newUrl);
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there is no submission waiting for assessment at the moment
                    this.goToExerciseDashboard();
                    this.jhiAlertService.info('artemisApp.tutorExerciseDashboard.noSubmissions');
                } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.goToExerciseDashboard();
                } else {
                    this.onError('modelingAssessmentEditor.messages.loadSubmissionFailed');
                }
            },
        );
    }

    /**
     * @function ngAfterViewInit
     * @desc After the view was initialized, we create an interact.js resizable object,
     *       designate the edges which can be used to resize the target element and set min and max values.
     *       The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinWidth = this.$window.nativeWindow.screen.width / 6;
        this.resizableMinHeight = this.$window.nativeWindow.screen.height / 7;

        this.interactResizable = interact('.resizable-submission')
            .resizable({
                // Enable resize from left edge; triggered by class .resizing-bar
                edges: { left: '.resizing-bar', right: false, bottom: false, top: false },
                // Set min and max width
                restrictSize: {
                    min: { width: this.resizableMinWidth },
                    max: { width: this.resizableMaxWidth },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event) {
                const target = event.target;
                // Update element width
                target.style.width = event.rect.width + 'px';
                target.style.minWidth = event.rect.width + 'px';
            });

        this.interactResizableTop = interact('.resizable-horizontal')
            .resizable({
                // Enable resize from bottom edge; triggered by class .resizing-bar-bottom
                edges: { left: false, right: false, top: false, bottom: '.resizing-bar-bottom' },
                // Set min height
                restrictSize: {
                    min: { height: this.resizableMinHeight },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event) {
                const target = event.target;
                // Update element height
                target.style.minHeight = event.rect.height + 'px';
                $('#submission-area').css('min-height', event.rect.height - 100 + 'px');
            });
    }

    public ngOnDestroy(): void {
        this.changeDetectorRef.detach();
    }

    public addReferencedFeedback(): void {
        const referencedFeedback = new Feedback();
        referencedFeedback.credits = 0;
        this.referencedFeedback.push(referencedFeedback);
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        const indexToDelete = this.referencedFeedback.indexOf(assessmentToDelete);
        this.referencedFeedback.splice(indexToDelete, 1);
        this.validateAssessment();
    }

    /**
     * Load next assessment in the same page.
     * It calls the api to load the new unassessed submission in the same page.
     * For the new submission to appear on the same page, the url has to be reloaded.
     */
    assessNextOptimal() {
        if (this.exercise.type === ExerciseType.FILE_UPLOAD) {
            this.fileUploadSubmissionService.getFileUploadSubmissionForExerciseWithoutAssessment(this.exercise.id).subscribe(
                (response: FileUploadSubmission) => {
                    this.unassessedSubmission = response;
                    this.router.onSameUrlNavigation = 'reload';
                    // navigate to the new assessment page to trigger re-initialization of the components
                    this.router.navigateByUrl(`/file-upload-exercise/${this.exercise.id}/submission/${this.unassessedSubmission.id}/assessment`, {});
                },
                (error: HttpErrorResponse) => {
                    if (error.status === 404) {
                        // there are no unassessed submission, nothing we have to worry about
                        this.jhiAlertService.error('artemisApp.tutorExerciseDashboard.noSubmissions');
                    } else {
                        this.onError(error.message);
                    }
                },
            );
        }
    }

    public save(submit: boolean): void {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.fileUploadAssessmentsService.saveAssessment(this.assessments, this.submission.id, submit).subscribe(
            result => {
                this.result = result;
                this.updateParticipationWithResult();
                this.jhiAlertService.success('artemisApp.textAssessment.saveSuccessful');
            },
            (error: HttpErrorResponse) => this.onError(`artemisApp.${error.error.entityName}.${error.error.message}`),
        );
    }

    /**
     * Cancel the current assessment and navigate back to the exercise dashboard.
     */
    cancelAssessment() {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.fileUploadAssessmentsService.cancelAssessment(this.submission.id).subscribe(() => {
                this.goToExerciseDashboard();
            });
        }
    }

    private loadFeedbacks(feedbacks: Feedback[]): void {
        const generalFeedbackIndex = feedbacks.findIndex(feedback => feedback.reference == null);
        if (generalFeedbackIndex !== -1) {
            this.generalFeedback = feedbacks[generalFeedbackIndex];
            feedbacks.splice(generalFeedbackIndex, 1);
        } else {
            this.generalFeedback = new Feedback();
        }
        this.referencedFeedback = feedbacks;
    }

    private updateParticipationWithResult(): void {
        this.showResult = false;
        this.changeDetectorRef.detectChanges();
        this.participation.results[0] = this.result;
        this.showResult = true;
        this.changeDetectorRef.detectChanges();
    }

    private loadSubmission(submissionId: number): void {
        this.fileUploadSubmissionService
            .get(submissionId)
            .pipe(filter(res => !!res))
            .subscribe(
                res => {
                    this.handleReceivedSubmission(res.body!);
                },
                (error: HttpErrorResponse) => {
                    if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                        this.goToExerciseDashboard();
                    } else {
                        this.onError('');
                    }
                },
            );
    }

    private handleReceivedSubmission(submission: FileUploadSubmission): void {
        this.submission = submission;
        const studentParticipation = this.submission.participation as StudentParticipation;
        this.exercise = studentParticipation.exercise as FileUploadExercise;
        this.result = this.submission.result;
        if (this.result.hasComplaint) {
            this.getComplaint();
        }
        if (!this.result.feedbacks) {
            this.result.feedbacks = [];
        }
        this.submission.participation.results = [this.result];
        this.result.participation = this.submission.participation;
        if ((this.result.assessor == null || this.result.assessor.id === this.userId) && !this.result.completionDate) {
            this.jhiAlertService.clear();
            this.jhiAlertService.info('artemisApp.fileUploadAssessment.messages.lock');
        }
        this.checkPermissions();
        this.busy = false;
    }

    getComplaint(): void {
        this.complaintService.findByResultId(this.result.id).subscribe(
            res => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        );
    }
    goToExerciseDashboard() {
        if (this.exercise && this.exercise.course) {
            this.router.navigateByUrl(`/course/${this.exercise.course.id}/exercise/${this.exercise.id}/tutor-dashboard`);
        } else {
            this.location.back();
        }
    }

    /**
     * Checks if the assessment is valid:
     *   - There must be at least one referenced feedback or a general feedback.
     *   - Each reference feedback must have either a score or a feedback text or both.
     *   - The score must be a valid number.
     *
     * Additionally, the total score is calculated for all numerical credits.
     */
    public validateAssessment() {
        this.assessmentsAreValid = true;
        this.invalidError = null;

        if ((this.generalFeedback.detailText == null || this.generalFeedback.detailText.length === 0) && this.referencedFeedback && this.referencedFeedback.length === 0) {
            this.totalScore = 0;
            this.assessmentsAreValid = false;
            return;
        }

        let credits = this.referencedFeedback.map(assessment => assessment.credits);

        if (!this.invalidError && !credits.every(credit => credit !== null && !isNaN(credit))) {
            this.invalidError = 'artemisApp.textAssessment.error.invalidScoreMustBeNumber';
            this.assessmentsAreValid = false;
            credits = credits.filter(credit => credit !== null && !isNaN(credit));
        }

        if (!this.invalidError && !this.referencedFeedback.every(f => f.credits !== 0 || (f.detailText != null && f.detailText.length > 0))) {
            this.invalidError = 'artemisApp.textAssessment.error.invalidNeedScoreOrFeedback';
            this.assessmentsAreValid = false;
        }

        this.totalScore = credits.reduce((a, b) => a + b, 0);
    }

    private checkPermissions() {
        this.isAssessor = this.result && this.result.assessor && this.result.assessor.id === this.userId;
        const isBeforeAssessmentDueDate = this.exercise && this.exercise.assessmentDueDate && moment().isBefore(this.exercise.assessmentDueDate);
        // tutors are allowed to override one of their assessments before the assessment due date, instructors can override any assessment at any time
        this.canOverride = (this.isAssessor && isBeforeAssessmentDueDate) || this.isAtLeastInstructor;
    }

    toggleCollapse($event: any) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        const $card = $(target).closest('#instructions');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
            this.interactResizable.resizable({ enabled: true });
            $card.css({ width: this.resizableMinWidth + 'px', minWidth: this.resizableMinWidth + 'px' });
        } else {
            $card.addClass('collapsed');
            $card.css({ width: '55px', minWidth: '55px' });
            this.interactResizable.resizable({ enabled: false });
        }
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    onUpdateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.fileUploadAssessmentsService.updateAssessmentAfterComplaint(this.assessments, complaintResponse, this.submission.id).subscribe(
            result => {
                this.result = result;
                this.updateParticipationWithResult();
                this.jhiAlertService.clear();
                this.jhiAlertService.success('artemisApp.fileUploadAssessment.updateAfterComplaintSuccessful');
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.clear();
                this.jhiAlertService.error('artemisApp.fileUploadAssessment.updateAfterComplaintFailed');
            },
        );
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
