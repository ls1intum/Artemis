import * as $ from 'jquery';
import { AfterViewInit, Component, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { TextAssessmentBaseComponent } from 'app/exercises/text/assess/text-assessment-base.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { notUndefined } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-example-text-submission',
    templateUrl: './example-text-submission.component.html',
    providers: [],
    styleUrls: ['./example-text-submission.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ExampleTextSubmissionComponent extends TextAssessmentBaseComponent implements OnInit, AfterViewInit {
    isNewSubmission: boolean;
    areNewAssessments = true;
    exerciseId: number;
    exampleSubmission = new ExampleSubmission();
    assessmentsAreValid = false;
    result?: Result;
    totalScore: number;
    invalidError?: string;
    readOnly: boolean;
    toComplete: boolean;

    resizableMinWidth = 100;
    resizableMaxWidth = 1200;
    resizableMinHeight = 200;
    interactResizableSubmission: Interactable;
    interactResizableAssessment: Interactable;
    interactResizableTop: Interactable;

    private exampleSubmissionId: number;
    private unreferencedFeedback: Feedback[] = [];
    constructor(
        jhiAlertService: JhiAlertService,
        accountService: AccountService,
        assessmentsService: TextAssessmentsService,
        structuredGradingCriterionService: StructuredGradingCriterionService,
        private exerciseService: ExerciseService,
        private textSubmissionService: TextSubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
        private tutorParticipationService: TutorParticipationService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
        private artemisMarkdown: ArtemisMarkdownService,
        private resultService: ResultService,
        private guidedTourService: GuidedTourService,
    ) {
        super(jhiAlertService, accountService, assessmentsService, structuredGradingCriterionService);
        this.textBlockRefs = [];
        this.unusedTextBlockRefs = [];
        this.submission = new TextSubmission();
    }

    private get referencedFeedback(): Feedback[] {
        return this.textBlockRefs.map(({ feedback }) => feedback).filter(notUndefined) as Feedback[];
    }

    private get assessments(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    /**
     * Reads route params and loads the example submission on init.
     */
    async ngOnInit(): Promise<void> {
        await super.ngOnInit();
        // (+) converts string 'id' to a number
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.queryParamMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.queryParamMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            this.exampleSubmissionId = +exampleSubmissionId!;
        }
        this.loadAll();
    }

    /**
     * Sets the size of resizable elements after initialization.
     */
    ngAfterViewInit(): void {
        this.resizableMinWidth = window.screen.width / 6;
        this.resizableMinHeight = window.screen.height / 7;

        this.interactResizableSubmission = interact('.resizable-submission')
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
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element width
                target.style.width = event.rect.width + 'px';
                target.style.minWidth = event.rect.width + 'px';
            });

        this.interactResizableAssessment = interact('.resizable-assessment')
            .resizable({
                // Enable resize from left edge; triggered by class .resizing-bar-assessment
                edges: { left: '.resizing-bar-assessment', right: false, bottom: false, top: false },
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
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
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
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeight },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element height
                target.style.minHeight = event.rect.height + 'px';
                $('#submission-area').css('min-height', event.rect.height - 100 + 'px');
            });

        this.guidedTourService.componentPageLoaded();
    }

    /**
     * Loads the exercise.
     * Also loads the example submission if the new parameter is not set.
     */
    loadAll() {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<TextExercise>) => {
            this.exercise = exerciseResponse.body!;
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorForExercise(this.exercise);
            this.guidedTourService.enableTourForExercise(this.exercise, tutorAssessmentTour, false);
        });

        if (this.isNewSubmission) {
            return; // We don't need to load anything else
        }

        this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.submission = this.exampleSubmission.submission as TextSubmission;

            // Do not load the results when we have to assess the submission. The API will not provide it anyway
            // if we are not instructors
            if (this.toComplete) {
                return;
            }
            this.fetchExampleResult();
        });
    }

    private fetchExampleResult() {
        this.assessmentsService.getExampleResult(this.exerciseId, this.submission?.id!).subscribe((result) => {
            this.result = result;
            this.submission = result.submission;
            this.prepareTextBlocksAndFeedbacks();
            this.areNewAssessments = this.assessments.length <= 0;
            this.validateFeedback();
        });
    }

    /**
     * Collapse/open instructions.
     * @param $event used to evaluate the target element
     * @param resizable determines if the resizable element is an assessment or submission of type {string}
     */
    toggleCollapse($event: any, resizable: string) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        let $card: any;

        if (resizable === 'submission') {
            $card = $(target).closest('#instructions');
        } else if (resizable === 'assessment') {
            $card = $(target).closest('#assessment-instructions');
        }

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
            if (resizable === 'submission') {
                this.interactResizableSubmission.resizable({ enabled: true });
            } else if (resizable === 'assessment') {
                this.interactResizableAssessment.resizable({ enabled: true });
            }
            $card.css({ width: this.resizableMinWidth + 'px', minWidth: this.resizableMinWidth + 'px' });
        } else {
            $card.addClass('collapsed');
            if (resizable === 'submission') {
                this.interactResizableSubmission.resizable({ enabled: false });
            } else if (resizable === 'assessment') {
                this.interactResizableAssessment.resizable({ enabled: false });
            }
            $card.css({ width: '55px', minWidth: '55px' });
        }
    }

    /**
     * Creates the example submission.
     */
    createNewExampleTextSubmission() {
        const newExampleSubmission = new ExampleSubmission();
        newExampleSubmission.submission = this.submission!;
        newExampleSubmission.exercise = this.exercise;

        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.exampleSubmission.exercise = this.exercise;
            this.exampleSubmissionId = this.exampleSubmission.id!;
            this.submission = this.exampleSubmission.submission as TextSubmission;
            this.isNewSubmission = false;

            // Update the url with the new id, without reloading the page, to make the history consistent
            const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
            this.location.go(newUrl);

            this.resultService.createNewExampleResult(this.submission.id!).subscribe((response: HttpResponse<Result>) => {
                this.result = response.body!;
                this.jhiAlertService.success('artemisApp.exampleSubmission.submitSuccessful');
                this.fetchExampleResult();
            }, this.onError);
        }, this.onError);
    }

    /**
     * Updates the example submission.
     */
    updateExampleTextSubmission() {
        this.exampleSubmissionService.update(this.exampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;

            this.jhiAlertService.success('artemisApp.exampleSubmission.saveSuccessful');
        }, this.onError);
    }

    /**
     * Checks if the score boundaries have been respected and save the assessment.
     */
    public saveAssessments(): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }
        this.assessmentsService.saveExampleAssessment(this.exampleSubmission.id!, this.assessments, this.textBlocksWithFeedback).subscribe((response) => {
            this.result = response.body!;
            this.areNewAssessments = false;
            this.jhiAlertService.success('artemisApp.textAssessment.saveSuccessful');
        });
    }

    /**
     * Redirects back to the assessment dashboard if route param readOnly or toComplete is set.
     * Otherwise redirects back to the exercise's edit view either for exam exercises or normal exercises.
     */
    async back() {
        const courseId = this.course?.id;
        // check if exam exercise
        if (!!this.exercise?.exerciseGroup) {
            const examId = this.exercise.exerciseGroup.exam?.id;
            const exerciseGroupId = this.exercise.exerciseGroup.id;
            if (this.readOnly || this.toComplete) {
                await this.router.navigate([`/course-management/${courseId}/exercises/${this.exerciseId}/tutor-dashboard`]);
            } else {
                await this.router.navigate(['/course-management', courseId, 'exams', examId, 'exercise-groups', exerciseGroupId, 'text-exercises', this.exerciseId, 'edit']);
            }
        } else {
            if (this.readOnly || this.toComplete) {
                this.router.navigate([`/course-management/${courseId}/exercises/${this.exerciseId}/tutor-dashboard`]);
            } else {
                await this.router.navigate(['/course-management', courseId, 'text-exercises']);
                this.router.navigate(['/course-management', courseId, 'text-exercises', this.exerciseId, 'edit']);
            }
        }
    }

    /**
     * Checks the assessment of the tutor to the example submission tutorial.
     * The tutor is informed if the given points of the assessment are fine, too low or too high.
     */
    checkAssessment() {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        const exampleSubmission = Object.assign({}, this.exampleSubmission);
        if (exampleSubmission.submission) {
            const result = getLatestSubmissionResult(exampleSubmission.submission);
            setLatestSubmissionResult(exampleSubmission.submission, result);
            result!.feedbacks = this.assessments;
        }
        this.tutorParticipationService.assessExampleSubmission(exampleSubmission, this.exerciseId).subscribe(
            () => {
                this.jhiAlertService.success('artemisApp.exampleSubmission.assessScore.success');
            },
            (error: HttpErrorResponse) => {
                const errorType = error.headers.get('x-artemisapp-error');

                if (errorType === 'error.tooLow') {
                    this.jhiAlertService.error('artemisApp.exampleSubmission.assessScore.tooLow');
                } else if (errorType === 'error.tooHigh') {
                    this.jhiAlertService.error('artemisApp.exampleSubmission.assessScore.tooHigh');
                } else {
                    this.onError(error.message);
                }
            },
        );
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        this.assessmentsAreValid = this.referencedFeedback.filter(Feedback.isValid).length > 0;
        this.totalScore = this.computeTotalScore(this.assessments);

        if (this.guidedTourService.currentTour && this.toComplete) {
            this.guidedTourService.updateAssessmentResult(this.assessments.length, this.totalScore);
        }
    }

    /**
     * After the tutor declared that he read and understood the example submission a corresponding submission will be added to the
     * tutor participation of the exercise. Then a success alert is invoked and the user gets redirected back.
     */
    readAndUnderstood() {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(() => {
            this.jhiAlertService.success('artemisApp.exampleSubmission.readSuccessfully');
            this.back();
        });
    }

    private onError(error: string) {
        this.jhiAlertService.error(error);
    }

    private prepareTextBlocksAndFeedbacks() {
        console.log(this.submission?.blocks, this.result?.feedbacks);
        const matchBlocksWithFeedbacks = TextAssessmentsService.matchBlocksWithFeedbacks(this.submission?.blocks || [], this.result?.feedbacks || []);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, this.textBlockRefs, this.unusedTextBlockRefs, this.submission);
    }
}
