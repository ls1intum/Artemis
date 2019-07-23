import * as $ from 'jquery';
import { SafeHtml } from '@angular/platform-browser';
import * as moment from 'moment';

import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TextExercise } from 'app/entities/text-exercise';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { HighlightColors } from '../text-shared/highlight-colors';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { Result, ResultService } from 'app/entities/result';
import { TextAssessmentsService } from 'app/entities/text-assessments/text-assessments.service';
import { Feedback } from 'app/entities/feedback';
import { Participation } from 'app/entities/participation';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { AccountService, WindowRef } from 'app/core';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { Complaint } from 'app/entities/complaint';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseType } from 'app/entities/exercise';
import { Subscription } from 'rxjs/Subscription';

@Component({
    providers: [TextAssessmentsService, WindowRef],
    templateUrl: './text-assessment.component.html',
    styleUrls: ['./text-assessment.component.scss'],
})
export class TextAssessmentComponent implements OnInit, OnDestroy, AfterViewInit {
    text: string;
    participation: Participation;
    submission: TextSubmission;
    unassessedSubmission: TextSubmission;
    result: Result;
    generalFeedback: Feedback;
    referencedFeedback: Feedback[];
    exercise: TextExercise;
    totalScore = 0;
    assessmentsAreValid: boolean;
    invalidError: string | null;
    isAuthorized = true;
    isAtLeastInstructor = false;
    busy = true;
    showResult = true;
    hasComplaint = false;
    complaint: Complaint;
    notFound = false;
    userId: number;
    canOverride = false;

    paramSub: Subscription;

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

    public getColorForIndex = HighlightColors.forIndex;

    constructor(
        private changeDetectorRef: ChangeDetectorRef,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private resultService: ResultService,
        private assessmentsService: TextAssessmentsService,
        private accountService: AccountService,
        private location: Location,
        private $window: WindowRef,
        private artemisMarkdown: ArtemisMarkdown,
        private translateService: TranslateService,
        private textSubmissionService: TextSubmissionService,
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

        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        this.paramSub = this.route.params.subscribe(params => {
            const exerciseId = Number(params['exerciseId']);
            const submissionValue = params['submissionId'];

            if (submissionValue === 'new') {
                this.assessmentsService.getParticipationForSubmissionWithoutAssessment(exerciseId).subscribe(
                    participation => {
                        this.receiveParticipation(participation);

                        // Update the url with the new id, without reloading the page, to make the history consistent
                        const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission.id}`);
                        this.location.go(newUrl);
                    },
                    (error: HttpErrorResponse) => {
                        if (error.status === 404) {
                            this.notFound = true;
                        } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                            this.goToExerciseDashboard();
                        } else {
                            this.onError(error.message);
                        }
                        this.busy = false;
                    },
                );
            } else {
                const submissionId = Number(submissionValue);
                this.assessmentsService
                    .getFeedbackDataForExerciseSubmission(exerciseId, submissionId)
                    .subscribe(participation => this.receiveParticipation(participation), (error: HttpErrorResponse) => this.onError(error.message));
            }
        });
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
        this.paramSub.unsubscribe();
    }

    public addAssessment(assessmentText: string): void {
        const assessment = new Feedback();
        assessment.reference = assessmentText;
        assessment.credits = 0;
        this.referencedFeedback.push(assessment);
        this.validateAssessment();
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        this.referencedFeedback = this.referencedFeedback.filter(elem => elem !== assessmentToDelete);
        this.validateAssessment();
    }

    public save(): void {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.assessmentsService.save(this.assessments, this.exercise.id, this.result.id).subscribe(
            response => {
                this.result = response.body!;
                this.updateParticipationWithResult();
                this.jhiAlertService.success('artemisApp.textAssessment.saveSuccessful');
            },
            (error: HttpErrorResponse) => this.onError(`artemisApp.${error.error.entityName}.${error.error.message}`),
        );
    }

    public submit(): void {
        if (!this.result.id) {
            return; // We need to have saved the result before
        }

        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.assessmentsService.submit(this.assessments, this.exercise.id, this.result.id).subscribe(
            response => {
                this.result = response.body!;
                this.updateParticipationWithResult();
                this.jhiAlertService.success('artemisApp.textAssessment.submitSuccessful');
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
            this.assessmentsService.cancelAssessment(this.exercise.id, this.submission.id).subscribe(() => {
                this.goToExerciseDashboard();
            });
        }
    }
    /**
     * Load next assessment in the same page.
     * It calls the api to load the new unassessed submission in the same page.
     * For the new submission to appear on the same page, the url has to be reloaded.
     */
    assessNextOptimal() {
        if (this.exercise.type === ExerciseType.TEXT) {
            this.textSubmissionService.getTextSubmissionForExerciseWithoutAssessment(this.exercise.id).subscribe(
                (response: TextSubmission) => {
                    this.unassessedSubmission = response;
                    this.router.onSameUrlNavigation = 'reload';
                    // navigate to the new assessment page to trigger re-initialization of the components
                    this.router.navigateByUrl(`/text/${this.exercise.id}/assessment/${this.unassessedSubmission.id}`, {});
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

    public predefineTextBlocks(): void {
        this.assessmentsService.getResultWithPredefinedTextblocks(this.result.id).subscribe(
            response => {
                this.loadFeedbacks(response.body!.feedbacks || []);
            },
            (error: HttpErrorResponse) => this.onError(error.message),
        );
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

    private receiveParticipation(participation: Participation): void {
        this.participation = participation;
        this.submission = <TextSubmission>this.participation.submissions[0];
        this.exercise = <TextExercise>this.participation.exercise;

        this.formattedGradingInstructions = this.artemisMarkdown.htmlForMarkdown(this.exercise.gradingInstructions);
        this.formattedProblemStatement = this.artemisMarkdown.htmlForMarkdown(this.exercise.problemStatement);
        this.formattedSampleSolution = this.artemisMarkdown.htmlForMarkdown(this.exercise.sampleSolution);

        this.result = this.participation.results[0];
        this.hasComplaint = this.result.hasComplaint;

        this.loadFeedbacks(this.result.feedbacks || []);
        this.busy = false;
        this.validateAssessment();
        this.checkPermissions();
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
     *   - There must be at least one feedback referencing a text element or a general feedback.
     *   - Each reference feedback must have either a score or a feedback text or both.
     *   - The score must be a valid number.
     *
     * Additionally, the total score is calculated if the current assessment is valid.
     */
    public validateAssessment() {
        if ((this.generalFeedback.detailText == null || this.generalFeedback.detailText.length === 0) && this.referencedFeedback && this.referencedFeedback.length === 0) {
            this.totalScore = 0;
            this.assessmentsAreValid = false;
            return;
        }

        if (!this.referencedFeedback.every(f => f.reference != null && f.reference.length <= 2000)) {
            this.invalidError = 'artemisApp.textAssessment.error.feedbackReferenceTooLong';
            this.assessmentsAreValid = false;
            return;
        }

        const credits = this.referencedFeedback.map(assessment => assessment.credits);

        if (!credits.every(credit => credit !== null && !isNaN(credit))) {
            this.invalidError = 'artemisApp.textAssessment.error.invalidScoreMustBeNumber';
            this.assessmentsAreValid = false;
            return;
        }

        if (!this.referencedFeedback.every(f => f.credits !== 0 || (f.detailText != null && f.detailText.length > 0))) {
            this.invalidError = 'artemisApp.textAssessment.error.invalidNeedScoreOrFeedback';
            this.assessmentsAreValid = false;
            return;
        }

        this.totalScore = credits.reduce((a, b) => a! + b!, 0)!;
        this.assessmentsAreValid = true;
        this.invalidError = null;
    }

    private checkPermissions() {
        this.isAuthorized = this.result && this.result.assessor && this.result.assessor.id === this.userId;
        const isBeforeAssessmentDueDate = this.exercise && this.exercise.assessmentDueDate && moment().isBefore(this.exercise.assessmentDueDate);
        // tutors are allowed to override one of their assessments before the assessment due date, instructors can override any assessment at any time
        this.canOverride = (this.isAuthorized && isBeforeAssessmentDueDate) || this.isAtLeastInstructor;
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

    public get headingTranslationKey(): string {
        const baseKey = 'artemisApp.textAssessment.heading.';

        if (this.submission && this.submission.exampleSubmission) {
            return baseKey + 'exampleAssessment';
        }
        return baseKey + 'assessment';
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

        this.assessmentsService.updateAfterComplaint(this.assessments, complaintResponse, this.exercise.id, this.result.id).subscribe(
            response => {
                this.result = response.body!;
                this.updateParticipationWithResult();
                this.jhiAlertService.clear();
                this.jhiAlertService.success('artemisApp.textAssessment.updateAfterComplaintSuccessful');
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.clear();
                this.jhiAlertService.error('artemisApp.textAssessment.updateAfterComplaintFailed');
            },
        );
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
