import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Result } from 'app/entities/result.model';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { Feedback, FeedbackCorrectionError } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { concatMap, tap } from 'rxjs/operators';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { getPositiveAndCappedTotalScore } from 'app/exercises/shared/exercise/exercise-utils';
import { onError } from 'app/shared/util/global.utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-example-modeling-submission',
    templateUrl: './example-modeling-submission.component.html',
    styleUrls: ['./example-modeling-submission.component.scss'],
})
export class ExampleModelingSubmissionComponent implements OnInit {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;
    @ViewChild(ModelingAssessmentComponent, { static: false })
    assessmentEditor: ModelingAssessmentComponent;

    isNewSubmission: boolean;
    usedForTutorial = false;
    assessmentMode = false;
    exerciseId: number;
    exampleSubmission: ExampleSubmission;
    modelingSubmission: ModelingSubmission;
    umlModel: UMLModel;
    explanationText: string;
    feedbacks: Feedback[] = [];
    feedbackChanged = false;
    assessmentsAreValid = false;
    result: Result;
    totalScore: number;
    invalidError?: string;
    exercise: ModelingExercise;
    isAtLeastEditor = false;
    isAtLeastInstructor = false;
    readOnly: boolean;
    toComplete: boolean;
    assessmentExplanation: string;
    isExamMode: boolean;

    legend = [
        {
            text: 'artemisApp.exampleSubmission.legend.positiveScore',
            icon: 'check' as IconProp,
            color: 'green',
            size: '2em',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.negativeScore',
            icon: 'times' as IconProp,
            color: 'red',
            size: '2em',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.feedbackWithoutScore',
            icon: 'exclamation' as IconProp,
            color: 'blue',
            size: '1.66em',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.incorrectAssessment',
            icon: 'exclamation-triangle' as IconProp,
            color: 'yellow',
            size: '2em',
        },
    ];

    private exampleSubmissionId: number;

    constructor(
        private exerciseService: ExerciseService,
        private exampleSubmissionService: ExampleSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private tutorParticipationService: TutorParticipationService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.queryParamMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.queryParamMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            // (+) converts string 'id' to a number
            this.exampleSubmissionId = +exampleSubmissionId!;
        }

        // if one of the flags is set, we navigated here from the assessment dashboard which means that we are not
        // interested in the modeling editor, i.e. we only wanna use the assessment mode
        if (this.readOnly || this.toComplete) {
            this.assessmentMode = true;
        }
        this.loadAll();
    }

    private loadAll(): void {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ModelingExercise>) => {
            this.exercise = exerciseResponse.body!;
            this.isExamMode = this.exercise.exerciseGroup != undefined;
            this.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(this.exercise.course || this.exercise.exerciseGroup!.exam!.course);
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course || this.exercise.exerciseGroup!.exam!.course);
        });

        if (this.isNewSubmission) {
            this.exampleSubmission = new ExampleSubmission();
            return; // We don't need to load anything else
        }

        this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            if (this.exampleSubmission.submission) {
                this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                if (this.modelingSubmission.model) {
                    this.umlModel = JSON.parse(this.modelingSubmission.model);
                }
                // Updates the explanation text with example modeling submission's explanation
                this.explanationText = this.modelingSubmission.explanationText ?? '';
            }
            this.usedForTutorial = this.exampleSubmission.usedForTutorial!;
            this.assessmentExplanation = this.exampleSubmission.assessmentExplanation!;

            // Do not load the results when we have to assess the submission. The API will not provide it anyway
            // if we are not instructors
            if (this.toComplete) {
                return;
            }

            this.modelingAssessmentService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id!).subscribe((result) => {
                if (result) {
                    this.result = result;
                    this.feedbacks = this.result.feedbacks || [];
                }
                this.checkScoreBoundaries();
            });
        });
    }

    upsertExampleModelingSubmission() {
        if (this.isNewSubmission) {
            this.createNewExampleModelingSubmission();
        } else {
            this.updateExampleModelingSubmission();
            this.updateAssessmentExplanationAndExampleAssessment();
        }
    }

    private createNewExampleModelingSubmission(): void {
        const modelingSubmission: ModelingSubmission = new ModelingSubmission();
        modelingSubmission.model = JSON.stringify(this.modelingEditor.getCurrentModel());
        modelingSubmission.explanationText = this.explanationText;
        modelingSubmission.exampleSubmission = true;

        const newExampleSubmission: ExampleSubmission = this.exampleSubmission;
        newExampleSubmission.submission = modelingSubmission;
        newExampleSubmission.exercise = this.exercise;
        newExampleSubmission.usedForTutorial = this.usedForTutorial;

        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe(
            (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmissionId = this.exampleSubmission.id!;
                if (this.exampleSubmission.submission) {
                    this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                    if (this.modelingSubmission.model) {
                        this.umlModel = JSON.parse(this.modelingSubmission.model);
                    }
                    // Updates the explanation text with example modeling submission's explanation
                    this.explanationText = this.modelingSubmission.explanationText ?? '';
                }
                this.isNewSubmission = false;

                this.jhiAlertService.success('artemisApp.modelingEditor.saveSuccessful');

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
                this.location.go(newUrl);
            },
            (error: HttpErrorResponse) => {
                onError(this.jhiAlertService, error);
            },
        );
    }

    private updateExampleModelingSubmission() {
        if (!this.modelingSubmission) {
            this.createNewExampleModelingSubmission();
        }
        this.modelingSubmission.model = JSON.stringify(this.modelingEditor.getCurrentModel());

        this.modelingSubmission.explanationText = this.explanationText;
        this.modelingSubmission.exampleSubmission = true;
        if (this.result) {
            this.result.feedbacks = this.feedbacks;
            setLatestSubmissionResult(this.modelingSubmission, this.result);
            delete this.result.submission;
        }

        const exampleSubmission = this.exampleSubmission;
        exampleSubmission.submission = this.modelingSubmission;
        exampleSubmission.exercise = this.exercise;
        exampleSubmission.usedForTutorial = this.usedForTutorial;
        this.exampleSubmissionService.update(exampleSubmission, this.exerciseId).subscribe(
            (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmissionId = this.exampleSubmission.id!;
                if (this.exampleSubmission.submission) {
                    this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                    if (this.modelingSubmission.model) {
                        this.umlModel = JSON.parse(this.modelingSubmission.model);
                    }
                    if (this.modelingSubmission.explanationText) {
                        this.explanationText = this.modelingSubmission.explanationText;
                    }
                }
                this.isNewSubmission = false;

                this.jhiAlertService.success('artemisApp.modelingEditor.saveSuccessful');
            },
            (error: HttpErrorResponse) => {
                onError(this.jhiAlertService, error);
            },
        );
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        this.feedbacks = feedbacks;
        this.feedbackChanged = true;
        this.checkScoreBoundaries();
    }

    showAssessment() {
        if (this.modelChanged()) {
            this.updateExampleModelingSubmission();
        }
        this.assessmentMode = true;
    }

    private modelChanged(): boolean {
        return this.modelingEditor && JSON.stringify(this.umlModel) !== JSON.stringify(this.modelingEditor.getCurrentModel());
    }

    explanationChanged(explanation: string) {
        this.explanationText = explanation;
    }

    showSubmission() {
        if (this.feedbackChanged) {
            this.saveExampleAssessment();
            this.feedbackChanged = false;
        }
        this.assessmentMode = false;
    }

    public saveExampleAssessment(): void {
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('modelingAssessment.invalidAssessments');
            return;
        }
        if (this.assessmentExplanation !== this.exampleSubmission.assessmentExplanation && this.feedbacks) {
            this.updateAssessmentExplanationAndExampleAssessment();
        } else if (this.assessmentExplanation !== this.exampleSubmission.assessmentExplanation) {
            this.updateAssessmentExplanation();
        } else if (this.feedbacks) {
            this.updateExampleAssessment();
        }
    }

    private updateAssessmentExplanationAndExampleAssessment() {
        this.exampleSubmission.assessmentExplanation = this.assessmentExplanation;
        this.exampleSubmissionService
            .update(this.exampleSubmission, this.exerciseId)
            .pipe(
                tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                    this.exampleSubmission = exampleSubmissionResponse.body!;
                    this.assessmentExplanation = this.exampleSubmission.assessmentExplanation!;
                }),
                concatMap(() => this.modelingAssessmentService.saveExampleAssessment(this.feedbacks, this.exampleSubmissionId)),
            )
            .subscribe(
                (result: Result) => {
                    this.result = result;
                    if (this.result) {
                        this.feedbacks = this.result.feedbacks!;
                    }
                    this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
                },
                () => {
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed');
                },
            );
    }

    /**
     * Updates the example submission with the assessment explanation text from the input field if it is different from the explanation already saved with the example submission.
     */
    private updateAssessmentExplanation() {
        this.exampleSubmission.assessmentExplanation = this.assessmentExplanation;
        this.exampleSubmissionService.update(this.exampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.assessmentExplanation = this.exampleSubmission.assessmentExplanation!;
        });
    }

    private updateExampleAssessment() {
        this.modelingAssessmentService.saveExampleAssessment(this.feedbacks, this.exampleSubmissionId).subscribe(
            (result: Result) => {
                this.result = result;
                if (this.result) {
                    this.feedbacks = this.result.feedbacks!;
                }
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            () => {
                this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed');
            },
        );
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     */
    public checkScoreBoundaries() {
        if (!this.feedbacks || this.feedbacks.length === 0) {
            this.totalScore = 0;
            this.assessmentsAreValid = true;
            return;
        }

        const credits = this.feedbacks.map((feedback) => feedback.credits);

        if (!credits.every((credit) => credit != undefined && !isNaN(credit))) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid = false;
            return;
        }

        const maxPoints = this.exercise.maxPoints! + this.exercise.bonusPoints!;
        const creditsTotalScore = credits.reduce((a, b) => a! + b!, 0)!;
        this.totalScore = getPositiveAndCappedTotalScore(creditsTotalScore, maxPoints);
        this.assessmentsAreValid = true;
        this.invalidError = undefined;
    }

    async back() {
        const courseId = this.exercise.course?.id || this.exercise.exerciseGroup?.exam?.course?.id;
        if (this.readOnly || this.toComplete) {
            await this.router.navigate(['/course-management', courseId, 'assessment-dashboard', this.exerciseId]);
        } else if (this.isExamMode) {
            await this.router.navigate([
                '/course-management',
                courseId,
                'exams',
                this.exercise.exerciseGroup?.exam?.id,
                'exercise-groups',
                this.exercise.exerciseGroup?.id,
                'modeling-exercises',
                this.exerciseId,
                'example-submissions',
            ]);
        } else {
            await this.router.navigate(['/course-management', courseId, 'modeling-exercises', this.exerciseId, 'example-submissions']);
        }
    }

    checkAssessment() {
        // scroll to top that the user definitely recognizes the response message (success OR score too low/high)
        window.scroll(0, 0);
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }

        const exampleSubmission = Object.assign({}, this.exampleSubmission);
        const result = new Result();
        setLatestSubmissionResult(exampleSubmission.submission, result);
        delete result.submission;
        getLatestSubmissionResult(exampleSubmission.submission)!.feedbacks = this.feedbacks;

        this.tutorParticipationService.assessExampleSubmission(exampleSubmission, this.exerciseId).subscribe(
            () => {
                this.markAllFeedbackToCorrect();
                this.jhiAlertService.success('artemisApp.exampleSubmission.assessScore.success');
            },
            (error: HttpErrorResponse) => {
                const errorType = error.headers.get('x-artemisapp-error');

                if (errorType === 'error.invalid_assessment') {
                    this.markAllFeedbackToCorrect();

                    // Mark all wrongly made feedbacks accordingly.
                    const correctionErrors: FeedbackCorrectionError[] = JSON.parse(error['error']['title'])['errors'];
                    correctionErrors.forEach((res) => {
                        const validatedFeedback = this.feedbacks.find((feedback) => feedback.reference === res.reference);
                        if (validatedFeedback != undefined) {
                            validatedFeedback.correctionStatus = res.type;
                        }
                    });

                    this.assessmentEditor.resultFeedbacks = this.feedbacks;

                    const errorMessage =
                        correctionErrors.length === 0 ? 'artemisApp.exampleSubmission.submissionValidation.missing' : 'artemisApp.exampleSubmission.submissionValidation.wrong';
                    this.jhiAlertService.error(errorMessage, { mistakeCount: correctionErrors.length });
                } else {
                    onError(this.jhiAlertService, error);
                }
            },
        );
    }

    markAllFeedbackToCorrect() {
        this.feedbacks.forEach((feedback) => {
            feedback.correctionStatus = 'CORRECT';
        });
        this.assessmentEditor.resultFeedbacks = this.feedbacks;
    }

    readAndUnderstood() {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(() => {
            this.jhiAlertService.success('artemisApp.exampleSubmission.readSuccessfully');
            this.back();
        });
    }
}
