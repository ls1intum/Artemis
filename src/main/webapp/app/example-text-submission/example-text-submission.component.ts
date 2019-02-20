import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService } from '../core';
import { ExampleSubmission } from 'app/entities/example-submission';
import { ExerciseService } from 'app/entities/exercise';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';
import { Feedback } from 'app/entities/feedback';
import { TextAssessmentsService } from 'app/entities/text-assessments/text-assessments.service';
import { Result } from 'app/entities/result';
import { HighlightColors } from 'app/text-shared/highlight-colors';
import { TextExercise } from 'app/entities/text-exercise';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { TutorParticipation } from 'app/entities/tutor-participation';

@Component({
    selector: 'jhi-example-text-submission',
    templateUrl: './example-text-submission.component.html',
    providers: [JhiAlertService]
})
export class ExampleTextSubmissionComponent implements OnInit {
    isNewSubmission: boolean;
    areNewAssessments = true;
    exerciseId: number;
    exampleSubmission = new ExampleSubmission();
    textSubmission = new TextSubmission();
    assessments: Feedback[] = [];
    assessmentsAreValid = false;
    result: Result;
    totalScore: number;
    invalidError: string;
    exercise: TextExercise;
    isAtLeastInstructor = false;
    readOnly: boolean;
    toComplete: boolean;

    public getColorForIndex = HighlightColors.forIndex;

    private exampleSubmissionId: number;
    constructor(
        private exerciseService: ExerciseService,
        private textSubmissionService: TextSubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
        private assessmentsService: TextAssessmentsService,
        private tutorParticipationService: TutorParticipationService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location
    ) {
    }

    ngOnInit(): void {
        // (+) converts string 'id' to a number
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.queryParamMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.queryParamMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            this.exampleSubmissionId = +exampleSubmissionId;
        }

        this.loadAll();
    }

    loadAll() {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<TextExercise>) => {
            this.exercise = exerciseResponse.body;
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course);
        });

        if (this.isNewSubmission) {
            return; // We don't need to load anything else
        }

        this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body;
            this.textSubmission = this.exampleSubmission.submission as TextSubmission;

            // Do not load the results when we have to assess the submission. The API will not provide it anyway
            // if we are not instructors
            if (this.toComplete) {
                return;
            }

            this.assessmentsService.getExampleAssessment(this.exerciseId, this.textSubmission.id).subscribe(result => {
                this.result = result;
                this.assessments = this.result.feedbacks || [];
                this.areNewAssessments = this.assessments.length <= 0;
                this.checkScoreBoundaries();
            });
        });
    }

    upsertExampleTextSubmission() {
        if (this.isNewSubmission) {
            this.createNewExampleTextSubmission();
        } else {
            this.updateExampleTextSubmission();
        }
    }

    private createNewExampleTextSubmission() {
        const newSubmission = this.textSubmission;
        newSubmission.exampleSubmission = true;

        this.textSubmissionService.create(newSubmission, this.exerciseId).subscribe((submissionResponse: HttpResponse<TextSubmission>) => {
            this.textSubmission = submissionResponse.body;

            const newExampleSubmission = this.exampleSubmission;
            newExampleSubmission.submission = this.textSubmission;
            newExampleSubmission.exercise = this.exercise;

            let bothCompleted = false;

            this.assessmentsService.getExampleAssessment(this.exerciseId, this.textSubmission.id).subscribe(result => {
                this.result = result;
                this.assessments = this.result.feedbacks || [];
                this.checkScoreBoundaries();

                if (bothCompleted) {
                    this.jhiAlertService.success('arTeMiSApp.exampleSubmission.submitSuccessful');
                }
                bothCompleted = true;
            }, this.onError);

            this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body;
                this.exampleSubmissionId = this.exampleSubmission.id;
                this.isNewSubmission = false;

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.exampleSubmissionId}`);
                this.location.go(newUrl);

                if (bothCompleted) {
                    this.jhiAlertService.success('arTeMiSApp.exampleSubmission.submitSuccessful');
                }
                bothCompleted = true;
            }, this.onError);
        }, this.onError);
    }

    private updateExampleTextSubmission() {
        this.textSubmission.exampleSubmission = true;

        let hasOneFinished = false;

        this.textSubmissionService.update(this.textSubmission, this.exerciseId).subscribe((submissionResponse: HttpResponse<TextSubmission>) => {
            this.textSubmission = submissionResponse.body;

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
            this.jhiAlertService.error('arTeMiSApp.textAssessment.invalidAssessments');
            return;
        }

        this.assessmentsService.save(this.assessments, this.exercise.id, this.result.id).subscribe(response => {
            this.result = response.body;
            this.areNewAssessments = false;
            this.jhiAlertService.success('arTeMiSApp.textAssessment.saveSuccessful');
        });
    }

    async back() {
        const courseId = this.exercise.course.id;

        if (this.readOnly || this.toComplete) {
            this.router.navigate([`/course/${courseId}/exercise/${this.exerciseId}/tutor-dashboard`]);
        } else {
            await this.router.navigate([`/course/${courseId}/text-exercise/`]);
            this.router.navigate(['/', {outlets: {popup: 'text-exercise/' + this.exerciseId + '/edit'}}]);
        }
    }

    checkAssessment() {
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('arTeMiSApp.textAssessment.invalidAssessments');
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
