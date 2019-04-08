import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService } from '../core';
import { ExampleSubmission } from 'app/entities/example-submission';
import { ExerciseService } from 'app/entities/exercise';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';
import { Feedback } from 'app/entities/feedback';
import { Result } from 'app/entities/result';
import { ModelingExercise, ModelingExercisePopupService } from 'app/entities/modeling-exercise';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { TutorParticipation } from 'app/entities/tutor-participation';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingAssessmentService } from 'app/modeling-assessment-editor';
import { ModelingAssessmentComponent } from 'app/modeling-assessment';

@Component({
    selector: 'jhi-example-modeling-submission',
    templateUrl: './example-modeling-submission.component.html',
    styleUrls: ['./example-modeling-submission.component.scss'],
})
export class ExampleModelingSubmissionComponent implements OnInit {
    @ViewChild(ModelingEditorComponent)
    modelingEditor: ModelingEditorComponent;
    @ViewChild(ModelingAssessmentComponent)
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
    invalidError: string;
    exercise: ModelingExercise;
    isAtLeastInstructor = false;
    readOnly: boolean;
    toComplete: boolean;

    private exampleSubmissionId: number;

    constructor(
        private exerciseService: ExerciseService,
        private modelingSubmissionService: ModelingSubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private tutorParticipationService: TutorParticipationService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private modelingExercisePopupService: ModelingExercisePopupService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.paramMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.paramMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            // (+) converts string 'id' to a number
            this.exampleSubmissionId = +exampleSubmissionId;
        }

        // Make sure the modeling exercise popup gets closed
        this.modelingExercisePopupService.close();

        this.loadAll();
    }

    private loadAll(): void {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ModelingExercise>) => {
            this.exercise = exerciseResponse.body;
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course);
        });

        if (this.isNewSubmission) {
            this.exampleSubmission = new ExampleSubmission();
            return; // We don't need to load anything else
        }

        this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body;
            if (this.exampleSubmission.submission) {
                this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                if (this.modelingSubmission.model) {
                    this.umlModel = JSON.parse(this.modelingSubmission.model);
                }
            }
            this.usedForTutorial = this.exampleSubmission.usedForTutorial;

            console.log(this.exampleSubmission);

            // Do not load the results when we have to assess the submission. The API will not provide it anyway
            // if we are not instructors
            if (this.toComplete) {
                return;
            }

            this.modelingAssessmentService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id).subscribe(result => {
                console.log(result);
                if (result) {
                    this.result = result;
                    this.feedbacks = this.result.feedbacks || [];
                    this.checkScoreBoundaries();
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

        this.modelingSubmissionService.create(modelingSubmission, this.exerciseId).subscribe((modelingSubmissionResponse: HttpResponse<ModelingSubmission>) => {
            this.modelingSubmission = modelingSubmissionResponse.body;
            if (this.modelingSubmission.model) {
                this.umlModel = JSON.parse(this.modelingSubmission.model);
            }

            const newExampleSubmission: ExampleSubmission = this.exampleSubmission;
            newExampleSubmission.submission = modelingSubmission;
            newExampleSubmission.exercise = this.exercise;
            newExampleSubmission.usedForTutorial = this.usedForTutorial;

            this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe(
                (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                    this.exampleSubmission = exampleSubmissionResponse.body;
                    this.exampleSubmissionId = this.exampleSubmission.id;
                    // if (this.exampleSubmission.submission) {
                    //     this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                    //     if (this.modelingSubmission.model) {
                    //         this.umlModel = JSON.parse(this.modelingSubmission.model);
                    //     }
                    // }
                    this.isNewSubmission = false;

                    this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');

                    // Update the url with the new id, without reloading the page, to make the history consistent
                    const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
                    this.location.go(newUrl);
                },
                (error: HttpErrorResponse) => {
                    console.error(error);
                    this.jhiAlertService.error(error.message);
                },
            );
        });

        // const newExampleSubmission: ExampleSubmission = this.exampleSubmission;
        // newExampleSubmission.submission = modelingSubmission;
        // newExampleSubmission.exercise = this.exercise;
        // newExampleSubmission.usedForTutorial = this.usedForTutorial;

        // this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe(
        //     (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
        //         this.exampleSubmission = exampleSubmissionResponse.body;
        //         this.exampleSubmissionId = this.exampleSubmission.id;
        //         if (this.exampleSubmission.submission) {
        //             this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
        //             if (this.modelingSubmission.model) {
        //                 this.umlModel = JSON.parse(this.modelingSubmission.model);
        //             }
        //         }
        //         this.isNewSubmission = false;
        //
        //         this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
        //
        //         // Update the url with the new id, without reloading the page, to make the history consistent
        //         const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
        //         this.location.go(newUrl);
        //     },
        //     (error: HttpErrorResponse) => {
        //         console.error(error);
        //         this.jhiAlertService.error(error.message);
        //     },
        // );
    }

    private updateExampleModelingSubmission() {
        if (!this.modelingSubmission) {
            this.modelingSubmission = new ModelingSubmission();
        }
        this.modelingSubmission.model = JSON.stringify(this.modelingEditor.getCurrentModel());
        this.modelingSubmission.exampleSubmission = true;
        if (this.result) {
            this.result.feedbacks = this.feedbacks;
            this.modelingSubmission.result = this.result;
        }

        this.modelingSubmissionService.update(this.modelingSubmission, this.exerciseId).subscribe(
            (modelingSubmissionResponse: HttpResponse<ModelingSubmission>) => {
                this.modelingSubmission = modelingSubmissionResponse.body;
                if (this.modelingSubmission.model) {
                    this.umlModel = JSON.parse(this.modelingSubmission.model);
                }

                const exampleSubmission = this.exampleSubmission;
                exampleSubmission.submission = this.modelingSubmission;
                exampleSubmission.exercise = this.exercise;
                exampleSubmission.usedForTutorial = this.usedForTutorial;

                this.exampleSubmissionService.update(exampleSubmission, this.exerciseId).subscribe(
                    (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                        this.exampleSubmission = exampleSubmissionResponse.body;
                        this.exampleSubmissionId = this.exampleSubmission.id;

                        this.jhiAlertService.success('arTeMiSApp.exampleSubmission.saveSuccessful');
                    },
                    (error: HttpErrorResponse) => {
                        console.error(error);
                        this.jhiAlertService.error(error.message);
                    },
                );
            },
            (error: HttpErrorResponse) => {
                console.error(error);
                this.jhiAlertService.error(error.message);
            },
        );

        // const exampleSubmission = this.exampleSubmission;
        // exampleSubmission.submission = this.modelingSubmission;
        // exampleSubmission.exercise = this.exercise;
        // exampleSubmission.usedForTutorial = this.usedForTutorial;
        // // TODO CZ: workaround to prevent 'org.hibernate.AssertionFailure: non-transient entity has a null id: de.tum.in.www1.artemis.domain.Result' -> investigate why/if this is necessary
        // if (exampleSubmission.submission.result) {
        //     exampleSubmission.submission.result = null;
        //     console.log('removed result');
        // }
        //
        // console.log(exampleSubmission);
        //
        // this.exampleSubmissionService.update(exampleSubmission, this.exerciseId).subscribe(
        //     (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
        //         this.exampleSubmission = exampleSubmissionResponse.body;
        //         this.exampleSubmissionId = this.exampleSubmission.id;
        //         if (this.exampleSubmission.submission) {
        //             this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
        //             if (this.modelingSubmission.model) {
        //                 this.umlModel = JSON.parse(this.modelingSubmission.model);
        //             }
        //         }
        //         this.isNewSubmission = false;
        //
        //         this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
        //     },
        //     (error: HttpErrorResponse) => {
        //         console.error(error);
        //         this.jhiAlertService.error(error.message);
        //     },
        // );
    }

    onFeedbackChanged(feedbacks: Feedback[]) {
        this.feedbacks = feedbacks;
        this.feedbackChanged = true;
        // this.checkScoreBoundaries(); TODO CZ: necessary?
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
        if (this.feedbacks) {
            let resultId = -1;
            if (this.result) {
                resultId = this.result.id;
            }
            this.modelingAssessmentService.saveExampleAssessment(this.feedbacks, this.exerciseId, resultId).subscribe(
                (result: Result) => {
                    this.result = result;
                    if (this.result) {
                        this.feedbacks = this.result.feedbacks;
                    }
                    this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
                },
                (error: HttpErrorResponse) => {
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed');
                },
            );
        }
    }

    public addAssessment(assessmentText: string): void {
        const assessment = new Feedback();
        assessment.reference = assessmentText;
        assessment.credits = 0;
        this.feedbacks.push(assessment);
        this.checkScoreBoundaries();
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        this.feedbacks = this.feedbacks.filter(elem => elem !== assessmentToDelete);
        this.checkScoreBoundaries();
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

        const credits = this.feedbacks.map(assessment => assessment.credits);

        if (!credits.every(credit => credit !== null && !isNaN(credit))) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid = false;
            return;
        }

        this.totalScore = credits.reduce((a, b) => a + b, 0);
        this.assessmentsAreValid = true;
        this.invalidError = null;
    }

    public saveAssessments(): void {
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('arTeMiSApp.modelingAssessment.invalidAssessments');
            return;
        }

        // this.assessmentsService.save(this.feedbacks, this.exercise.id, this.result.id).subscribe(response => {
        //     this.result = response.body;
        //     this.areNewAssessments = false;
        //     this.jhiAlertService.success('arTeMiSApp.modelingAssessment.saveSuccessful');
        // });
    }

    async back() {
        const courseId = this.exercise.course.id;

        if (this.readOnly || this.toComplete) {
            this.router.navigate([`/course/${courseId}/exercise/${this.exerciseId}/tutor-dashboard`]);
        } else {
            await this.router.navigate([`/course/${courseId}/`]);
            this.router.navigate(['/', { outlets: { popup: 'modeling-exercise/' + this.exerciseId + '/edit' } }]);
        }
    }

    checkAssessment() {
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('arTeMiSApp.modelingAssessment.invalidAssessments');
            return;
        }

        const exampleSubmission = Object.assign({}, this.exampleSubmission);
        exampleSubmission.submission.result = new Result();
        exampleSubmission.submission.result.feedbacks = this.feedbacks;

        this.tutorParticipationService.assessExampleSubmission(exampleSubmission, this.exerciseId).subscribe(
            (res: HttpResponse<TutorParticipation>) => {
                this.jhiAlertService.success('arTeMiSApp.exampleSubmission.assessScore.success');
            },
            (error: HttpErrorResponse) => {
                const errorType = error.headers.get('x-artemisapp-error');

                if (errorType === 'error.tooLow') {
                    this.jhiAlertService.error('arTeMiSApp.exampleSubmission.assessScore.tooLow');
                } else if (errorType === 'error.tooHigh') {
                    this.jhiAlertService.error('arTeMiSApp.exampleSubmission.assessScore.tooHigh');
                } else {
                    console.error(error);
                    this.jhiAlertService.error(error.message);
                }
            },
        );
    }

    // readAndUnderstood() {
    //     this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(
    //         (res: HttpResponse<TutorParticipation>) => {
    //             this.jhiAlertService.success('arTeMiSApp.exampleSubmission.readSuccessfully');
    //         }
    //     );
    // }
}
