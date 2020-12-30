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
import { Feedback } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { concatMap, tap } from 'rxjs/operators';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';

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
    isAtLeastInstructor = false;
    readOnly: boolean;
    toComplete: boolean;
    assessmentExplanation: string;
    isExamMode: boolean;

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
            });
        });
    }

    upsertExampleModelingSubmission() {
        if (this.isNewSubmission) {
            this.createNewExampleModelingSubmission();
        } else {
            this.updateExampleModelingSubmission();
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
                this.jhiAlertService.error(error.message);
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
                this.jhiAlertService.error(error.message);
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

        this.totalScore = credits.reduce((a, b) => a! + b!, 0)!;
        this.assessmentsAreValid = true;
        this.invalidError = undefined;
    }

    async back() {
        const courseId = this.exercise.course?.id || this.exercise.exerciseGroup?.exam?.course?.id;
        if (this.readOnly || this.toComplete) {
            await this.router.navigate(['/course-management', courseId, 'exercises', this.exerciseId, 'tutor-dashboard']);
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
                'edit',
            ]);
        } else {
            await this.router.navigate(['/course-management', courseId, 'modeling-exercises', this.exerciseId, 'edit']);
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

        setLatestSubmissionResult(exampleSubmission.submission, new Result());
        getLatestSubmissionResult(exampleSubmission.submission)!.feedbacks = this.feedbacks;

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
                    this.jhiAlertService.error(error.message);
                }
            },
        );
    }

    readAndUnderstood() {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(() => {
            this.jhiAlertService.success('artemisApp.exampleSubmission.readSuccessfully');
            this.back();
        });
    }
}
