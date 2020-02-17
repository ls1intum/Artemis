import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService } from 'app/core/auth/account.service';
import { ExampleSubmission } from 'app/entities/example-submission/example-submission.model';
import { ExerciseService } from 'app/entities/exercise';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ModelingSubmission } from 'app/entities/modeling-submission';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';
import { Feedback } from 'app/entities/feedback';
import { Result } from 'app/entities/result';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { TutorParticipation } from 'app/entities/tutor-participation';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingAssessmentComponent } from 'app/modeling-assessment/modeling-assessment.component';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';

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
    feedbacks: Feedback[] = [];
    feedbackChanged = false;
    assessmentsAreValid = false;
    result: Result;
    totalScore: number;
    invalidError: string | null;
    exercise: ModelingExercise;
    isAtLeastInstructor = false;
    readOnly: boolean;
    toComplete: boolean;
    assessmentExplanation: string;

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

        // if one of the flags is set, we navigated here from the tutor dashboard which means that we are not
        // interested in the modeling editor, i.e. we only wanna use the assessment mode
        if (this.readOnly || this.toComplete) {
            this.assessmentMode = true;
        }
        this.loadAll();
    }

    private loadAll(): void {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ModelingExercise>) => {
            this.exercise = exerciseResponse.body!;
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course!);
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
            }
            this.usedForTutorial = this.exampleSubmission.usedForTutorial;
            this.assessmentExplanation = this.exampleSubmission.assessmentExplanation;

            // Do not load the results when we have to assess the submission. The API will not provide it anyway
            // if we are not instructors
            if (this.toComplete) {
                return;
            }

            this.modelingAssessmentService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id).subscribe(result => {
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
        modelingSubmission.exampleSubmission = true;

        const newExampleSubmission: ExampleSubmission = this.exampleSubmission;
        newExampleSubmission.submission = modelingSubmission;
        newExampleSubmission.exercise = this.exercise;
        newExampleSubmission.usedForTutorial = this.usedForTutorial;

        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe(
            (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmissionId = this.exampleSubmission.id;
                if (this.exampleSubmission.submission) {
                    this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                    if (this.modelingSubmission.model) {
                        this.umlModel = JSON.parse(this.modelingSubmission.model);
                    }
                }
                this.isNewSubmission = false;

                this.jhiAlertService.success('artemisApp.modelingEditor.saveSuccessful');

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
                this.location.go(newUrl);
            },
            (error: HttpErrorResponse) => {
                console.error(error);
                this.jhiAlertService.error(error.message);
            },
        );
    }

    private updateExampleModelingSubmission() {
        if (!this.modelingSubmission) {
            this.createNewExampleModelingSubmission();
        }
        this.modelingSubmission.model = JSON.stringify(this.modelingEditor.getCurrentModel());
        this.modelingSubmission.exampleSubmission = true;
        if (this.result) {
            this.result.feedbacks = this.feedbacks;
            this.modelingSubmission.result = this.result;
        }

        const exampleSubmission = this.exampleSubmission;
        exampleSubmission.submission = this.modelingSubmission;
        exampleSubmission.exercise = this.exercise;
        exampleSubmission.usedForTutorial = this.usedForTutorial;

        this.exampleSubmissionService.update(exampleSubmission, this.exerciseId).subscribe(
            (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmissionId = this.exampleSubmission.id;
                if (this.exampleSubmission.submission) {
                    this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                    if (this.modelingSubmission.model) {
                        this.umlModel = JSON.parse(this.modelingSubmission.model);
                    }
                }
                this.isNewSubmission = false;

                this.jhiAlertService.success('artemisApp.modelingEditor.saveSuccessful');
            },
            (error: HttpErrorResponse) => {
                console.error(error);
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
            this.jhiAlertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }

        this.updateAssessmentExplanation();

        if (this.feedbacks) {
            this.modelingAssessmentService.saveExampleAssessment(this.feedbacks, this.exampleSubmissionId).subscribe(
                (result: Result) => {
                    this.result = result;
                    if (this.result) {
                        this.feedbacks = this.result.feedbacks;
                    }
                    this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
                },
                (error: HttpErrorResponse) => {
                    console.error(error);
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed');
                },
            );
        }
    }

    /**
     * Updates the example submission with the assessment explanation text from the input field if it is different from the explanation already saved with the example submission.
     */
    private updateAssessmentExplanation() {
        if (this.assessmentExplanation !== this.exampleSubmission.assessmentExplanation) {
            this.exampleSubmission.assessmentExplanation = this.assessmentExplanation;
            this.exampleSubmissionService.update(this.exampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.assessmentExplanation = this.exampleSubmission.assessmentExplanation;
            });
        }
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

        const credits = this.feedbacks.map(feedback => feedback.credits);

        if (!credits.every(credit => credit !== null && !isNaN(credit))) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid = false;
            return;
        }

        this.totalScore = credits.reduce((a, b) => a! + b!, 0)!;
        this.assessmentsAreValid = true;
        this.invalidError = null;
    }

    async back() {
        const courseId = this.exercise.course!.id;

        if (this.readOnly || this.toComplete) {
            this.router.navigate([`/course/${courseId}/exercise/${this.exerciseId}/tutor-dashboard`]);
        } else {
            await this.router.navigate([`/course/${courseId}/`]);
            this.router.navigate(['/modeling-exercise/' + this.exerciseId + '/edit']);
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
        exampleSubmission.submission.result = new Result();
        exampleSubmission.submission.result.feedbacks = this.feedbacks;

        this.tutorParticipationService.assessExampleSubmission(exampleSubmission, this.exerciseId).subscribe(
            (res: HttpResponse<TutorParticipation>) => {
                this.jhiAlertService.success('artemisApp.exampleSubmission.assessScore.success');
            },
            (error: HttpErrorResponse) => {
                const errorType = error.headers.get('x-artemisapp-error');

                if (errorType === 'error.tooLow') {
                    this.jhiAlertService.error('artemisApp.exampleSubmission.assessScore.tooLow');
                } else if (errorType === 'error.tooHigh') {
                    this.jhiAlertService.error('artemisApp.exampleSubmission.assessScore.tooHigh');
                } else {
                    console.error(error);
                    this.jhiAlertService.error(error.message);
                }
            },
        );
    }

    readAndUnderstood() {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe((res: HttpResponse<TutorParticipation>) => {
            this.jhiAlertService.success('artemisApp.exampleSubmission.readSuccessfully');
            this.back();
        });
    }
}
