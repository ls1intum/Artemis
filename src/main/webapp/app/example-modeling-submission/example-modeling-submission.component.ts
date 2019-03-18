import { Component, OnInit, ViewChildren, QueryList, AfterViewInit, ViewChild } from '@angular/core';
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
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { Result } from 'app/entities/result';
// import { HighlightColors } from 'app/text-shared/highlight-colors';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { TutorParticipation } from 'app/entities/tutor-participation';
import { ModelingEditorComponent } from 'app/modeling-editor';

@Component({
    selector: 'jhi-example-modeling-submission',
    templateUrl: './example-modeling-submission.component.html',
    providers: [JhiAlertService]
})
export class ExampleModelingSubmissionComponent implements OnInit, AfterViewInit {
    @ViewChildren('modelingEditor')
    editorList: QueryList<ModelingEditorComponent>;
    private modelingEditor: ModelingEditorComponent;

    isNewSubmission: boolean;
    areNewAssessments = true;
    exerciseId: number;
    exampleSubmission = new ExampleSubmission();
    modelingSubmission: ModelingSubmission;
    assessments: Feedback[] = [];
    assessmentsAreValid = false;
    result: Result;
    totalScore: number;
    invalidError: string;
    exercise: ModelingExercise;
    isAtLeastInstructor = false;
    readOnly: boolean;
    toComplete: boolean;

    // public getColorForIndex = HighlightColors.forIndex;

    private exampleSubmissionId: number;

    constructor(
        private exerciseService: ExerciseService,
        private modelingSubmissionService: ModelingSubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
        private assessmentsService: ModelingAssessmentService,
        private tutorParticipationService: TutorParticipationService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location
    ) {
    }

    ngOnInit(): void {
        console.log('from example modeling submission component');
        // (+) converts string 'id' to a number
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.paramMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.paramMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            this.exampleSubmissionId = +exampleSubmissionId;
        }

        this.exampleSubmission.usedForTutorial = false;

        this.loadAll();
    }

    private loadAll(): void {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ModelingExercise>) => {
            this.exercise = exerciseResponse.body;
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course); // TODO CZ: do we need this?
            if (this.modelingEditor) {
                this.modelingEditor.modelingExercise = this.exercise;
            }
        });

        if (this.isNewSubmission) {
            return; // We don't need to load anything else
        }

        this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body;
            this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
            if (this.modelingEditor) {
                this.modelingEditor.submission = this.modelingSubmission;
            }

            // Do not load the results when we have to assess the submission. The API will not provide it anyway
            // if we are not instructors
            if (this.toComplete) {
                return;
            }

            // this.assessmentsService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id).subscribe(result => {
            //     this.result = result;
            //     this.assessments = this.result.feedbacks || [];
            //     this.areNewAssessments = this.assessments.length <= 0;
            //     this.checkScoreBoundaries();
            // });
        });
    }

    /**
     * Load exercise and submission from server and pass it on to the modeling editor.
     * We cannot do this in ngOnInit() as the modelingEditor is undefined before the view is initialized.
     */
    ngAfterViewInit(): void {
        this.editorList.changes.subscribe((editors: QueryList<ModelingEditorComponent>) => {
            this.modelingEditor = editors.first;
            this.passDataToEditor();
        });
    }

    private passDataToEditor(): void {
        if (this.modelingEditor) {
            if (this.exercise) {
                this.modelingEditor.modelingExercise = this.exercise;
            }
            if (this.modelingSubmission) {
                this.modelingEditor.submission = this.modelingSubmission;
            }
        }
    }

    upsertExampleModelingSubmission() {
        if (this.isNewSubmission) {
            this.createNewExampleModelingSubmission();
        } else {
            this.updateExampleModelingSubmission();
        }
    }

    private createNewExampleModelingSubmission(): void {
        const newExampleSubmission = this.exampleSubmission;
        newExampleSubmission.submission = this.modelingEditor.getCurrentState();
        newExampleSubmission.submission.exampleSubmission = true;
        newExampleSubmission.exercise = this.exercise;
        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId)
            .subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body;
                this.exampleSubmissionId = this.exampleSubmission.id;
                this.isNewSubmission = false;

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
                this.location.go(newUrl);
        }, this.onError);
    }

    // private createNewExampleModelingSubmissionOld() {
    //     const newSubmission = this.modelingEditor.getCurrentState();
    //     newSubmission.exampleSubmission = true;
    //
    //     this.modelingSubmissionService.create(newSubmission, this.exercise.course.id, this.exerciseId)
    //         .subscribe((submissionResponse: HttpResponse<ModelingSubmission>) => {
    //             this.modelingSubmission = submissionResponse.body;
    //
    //             const newExampleSubmission = this.exampleSubmission;
    //             newExampleSubmission.submission = this.modelingSubmission;
    //             newExampleSubmission.exercise = this.exercise;
    //
    //             let bothCompleted = false;
    //
    //             this.assessmentsService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id).subscribe(result => {
    //                 this.result = result;
    //                 this.assessments = this.result.feedbacks || [];
    //                 this.checkScoreBoundaries();
    //
    //                 if (bothCompleted) {
    //                     this.jhiAlertService.success('arTeMiSApp.exampleSubmission.submitSuccessful');
    //                 }
    //                 bothCompleted = true;
    //             }, this.onError);
    //
    //             this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
    //                 this.exampleSubmission = exampleSubmissionResponse.body;
    //                 this.exampleSubmissionId = this.exampleSubmission.id;
    //                 this.isNewSubmission = false;
    //
    //                 // Update the url with the new id, without reloading the page, to make the history consistent
    //                 const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
    //                 this.location.go(newUrl);
    //
    //                 if (bothCompleted) {
    //                     this.jhiAlertService.success('arTeMiSApp.exampleSubmission.submitSuccessful');
    //                 }
    //                 bothCompleted = true;
    //             }, this.onError);
    //         }, this.onError);
    // }

    private updateExampleModelingSubmission() {
        this.modelingSubmission.exampleSubmission = true;

        let hasOneFinished = false;

        this.modelingSubmissionService.update(this.modelingSubmission, this.exercise.course.id, this.exerciseId)
            .subscribe((submissionResponse: HttpResponse<ModelingSubmission>) => {
                this.modelingSubmission = submissionResponse.body;

                if (hasOneFinished) {
                    this.jhiAlertService.success('arTeMiSApp.exampleSubmission.saveSuccessful');
                } else {
                    hasOneFinished = true;
                }
            }, this.onError);

        this.exampleSubmissionService.update(this.exampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body;

            if (hasOneFinished) {
                this.jhiAlertService.success('arTeMiSApp.exampleSubmission.saveSuccessful');
            } else {
                hasOneFinished = true;
            }
        }, this.onError);
    }

    public addAssessment(assessmentText: string): void {
        const assessment = new Feedback();
        assessment.reference = assessmentText;
        assessment.credits = 0;
        this.assessments.push(assessment);
        this.checkScoreBoundaries();
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        this.assessments = this.assessments.filter(elem => elem !== assessmentToDelete);
        this.checkScoreBoundaries();
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     */
    public checkScoreBoundaries() {
        if (!this.assessments || this.assessments.length === 0) {
            this.totalScore = 0;
            this.assessmentsAreValid = true;
            return;
        }

        const credits = this.assessments.map(assessment => assessment.credits);

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

        // this.assessmentsService.save(this.assessments, this.exercise.id, this.result.id).subscribe(response => {
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
            await this.router.navigate([`/course/${courseId}/modeling-exercise/`]);
            this.router.navigate(['/', {outlets: {popup: 'modeling-exercise/' + this.exerciseId + '/edit'}}]);
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
        exampleSubmission.submission.result.feedbacks = this.assessments;

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
                    this.onError(error.message);
                }
            }
        );
    }

    readAndUnderstood() {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(
            (res: HttpResponse<TutorParticipation>) => {
                this.jhiAlertService.success('arTeMiSApp.exampleSubmission.readSuccessfully');
            }
        );
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }
}
