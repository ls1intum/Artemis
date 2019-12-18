import * as $ from 'jquery';
import * as moment from 'moment';

import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TextExercise } from 'app/entities/text-exercise';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { HighlightColors } from './highlight-colors';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { Result, ResultService } from 'app/entities/result';
import { TextAssessmentsService } from 'app/entities/text-assessments/text-assessments.service';
import { Feedback } from 'app/entities/feedback';
import { StudentParticipation } from 'app/entities/participation';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { WindowRef } from 'app/core';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { Complaint, ComplaintType } from 'app/entities/complaint';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { ExerciseType } from 'app/entities/exercise';
import { Subscription } from 'rxjs/Subscription';
import { TextBlock } from 'app/entities/text-block/text-block.model';
import { AssessmentType } from 'app/entities/assessment-type';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    providers: [TextAssessmentsService, WindowRef],
    templateUrl: './text-assessment.component.html',
    styleUrls: ['./text-assessment.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TextAssessmentComponent implements OnInit, OnDestroy, AfterViewInit {
    text: string;
    participation: StudentParticipation;
    submission: TextSubmission;
    unassessedSubmission: TextSubmission;
    result: Result;
    generalFeedback: Feedback;
    referencedFeedback: Feedback[];
    referencedTextBlocks: (TextBlock | undefined)[];
    exercise: TextExercise;
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

    paramSub: Subscription;

    /** Resizable constants **/
    resizableMinWidth = 100;
    resizableMaxWidth = 1200;
    resizableMinHeight = 200;
    interactResizable: Interactable;
    interactResizableTop: Interactable;

    private cancelConfirmationText: string;

    public getColorForIndex = HighlightColors.forIndex;

    private readonly sha1Regex = /^[a-f0-9]{40}$/i;

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
                            this.onError(error);
                        }
                        this.busy = false;
                    },
                );
            } else {
                const submissionId = Number(submissionValue);
                this.assessmentsService.getFeedbackDataForExerciseSubmission(submissionId).subscribe(
                    participation => this.receiveParticipation(participation),
                    (error: HttpErrorResponse) => this.onError(error),
                );
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
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: this.resizableMinWidth, height: 0 },
                        max: { width: this.resizableMaxWidth, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event: any) {
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
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeight },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function(event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function(event: any) {
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
        this.referencedTextBlocks.push(undefined);
        this.validateAssessment();
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        const indexToDelete = this.referencedFeedback.indexOf(assessmentToDelete);
        this.referencedFeedback.splice(indexToDelete, 1);
        this.referencedTextBlocks.splice(indexToDelete, 1);
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
            (error: HttpErrorResponse) => this.onError(error),
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
            (error: HttpErrorResponse) => this.onError(error),
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
                        this.onError(error);
                    }
                },
            );
        }
    }

    public predefineTextBlocks(): void {
        this.assessmentsService.getResultWithPredefinedTextblocks(this.result.id).subscribe(
            response => {
                const submission = <TextSubmission>response.body!.submission;
                this.submission.blocks = submission.blocks;
                this.loadFeedbacks(response.body!.feedbacks || []);
                this.validateAssessment();
            },
            (error: HttpErrorResponse) => this.onError(error),
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

        /**
         * List of Text Blocks, where the order is IN SYNC with `referencedFeedback`.
         * referencedFeedback[i].reference == referencedTextBlocks[i].id
         * OR referencedTextBlocks[i] == undefined
         *
         * For all feedbacks, feedbacks[i].reference is defined.
         */
        this.referencedTextBlocks = feedbacks.map(feedback => {
            const feedbackReferencesTextBlock = feedback.reference ? this.sha1Regex.test(feedback.reference) : false;
            if (!feedbackReferencesTextBlock) {
                return undefined;
            }

            return this.submission.blocks!.find(block => block.id === feedback.reference);
        });
    }

    private updateParticipationWithResult(): void {
        this.showResult = false;
        this.changeDetectorRef.detectChanges();
        this.participation.results[0] = this.result;
        this.showResult = true;
        this.changeDetectorRef.detectChanges();
    }

    private receiveParticipation(participation: StudentParticipation): void {
        this.participation = participation;
        this.submission = <TextSubmission>this.participation.submissions[0];
        this.exercise = <TextExercise>this.participation.exercise;

        this.result = this.participation.results[0];
        if (this.result.hasComplaint) {
            this.getComplaint();
        }

        this.loadFeedbacks(this.result.feedbacks || []);
        this.busy = false;
        this.validateAssessment();
        this.checkPermissions();

        // Automatically fetch suggested Feedback for Automatic Assessment Enabled exercises.
        const needsAutomaticAssmentSuggestions = this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC && (!this.result.feedbacks || this.result.feedbacks.length === 0);
        if (needsAutomaticAssmentSuggestions) {
            this.predefineTextBlocks();
        }
    }

    getComplaint(): void {
        this.complaintService.findByResultId(this.result.id).subscribe(
            res => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            (error: HttpErrorResponse) => {
                this.onError(error);
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
     *   - There must be at least one feedback referencing a text element or a general feedback.
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

        if (!this.referencedFeedback.every(f => f.reference != null && f.reference.length <= 2000)) {
            this.invalidError = 'artemisApp.textAssessment.error.feedbackReferenceTooLong';
            this.assessmentsAreValid = false;
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

        this.assessmentsService.updateAssessmentAfterComplaint(this.assessments, complaintResponse, this.submission.id).subscribe(
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

    private onError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-artemisApp-message')!;
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
    }
}
