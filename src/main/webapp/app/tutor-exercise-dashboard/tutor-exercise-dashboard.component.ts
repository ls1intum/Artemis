import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseService, StatsForTutorDashboard } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService, User } from '../core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Exercise, ExerciseService, ExerciseType } from 'app/entities/exercise';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import { TutorParticipationService } from 'app/tutor-exercise-dashboard/tutor-participation.service';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { ExampleSubmission } from 'app/entities/example-submission';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { TextExercise } from 'app/entities/text-exercise';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { UMLModel } from '@ls1intum/apollon';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint } from 'app/entities/complaint';
import { Submission } from 'app/entities/submission';
import { ModelingSubmission, ModelingSubmissionService } from 'app/entities/modeling-submission';

export interface ExampleSubmissionQueryParams {
    readOnly?: boolean;
    toComplete?: boolean;
}

@Component({
    selector: 'jhi-courses',
    templateUrl: './tutor-exercise-dashboard.component.html',
    providers: [JhiAlertService, CourseService],
})
export class TutorExerciseDashboardComponent implements OnInit {
    exercise: Exercise;
    modelingExercise: ModelingExercise;
    courseId: number;
    exerciseId: number;
    numberOfTutorAssessments = 0;
    numberOfSubmissions = 0;
    numberOfAssessments = 0;
    numberOfComplaints = 0;
    numberOfTutorComplaints = 0;
    totalAssessmentPercentage = 0;
    tutorAssessmentPercentage = 0;
    tutorParticipationStatus: TutorParticipationStatus;
    submissions: Submission[] = [];
    unassessedSubmission: Submission;
    exampleSubmissionsToReview: ExampleSubmission[] = [];
    exampleSubmissionsToAssess: ExampleSubmission[] = [];
    exampleSubmissionsCompletedByTutor: ExampleSubmission[] = [];
    tutorParticipation: TutorParticipation;
    nextExampleSubmissionId: number;
    exampleSolutionModel: UMLModel;
    complaints: Complaint[];

    formattedGradingInstructions: string;
    formattedProblemStatement: string;
    formattedSampleSolution: string;

    readonly ExerciseType_TEXT = ExerciseType.TEXT;
    readonly ExerciseType_MODELING = ExerciseType.MODELING;

    stats = {
        toReview: {
            done: 0,
            total: 0,
        },
        toAssess: {
            done: 0,
            total: 0,
        },
    };

    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    tutor: User;

    constructor(
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private tutorParticipationService: TutorParticipationService,
        private textSubmissionService: TextSubmissionService,
        private modelingSubmissionService: ModelingSubmissionService,
        private artemisMarkdown: ArtemisMarkdown,
        private router: Router,
        private complaintService: ComplaintService,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));

        this.loadAll();

        this.accountService.identity().then(user => (this.tutor = user));
    }

    loadAll() {
        this.exerciseService.getForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => {
                this.exercise = res.body;
                this.formattedGradingInstructions = this.artemisMarkdown.htmlForMarkdown(this.exercise.gradingInstructions);
                this.formattedProblemStatement = this.artemisMarkdown.htmlForMarkdown(this.exercise.problemStatement);

                if (this.exercise.type === this.ExerciseType_TEXT) {
                    this.formattedSampleSolution = this.artemisMarkdown.htmlForMarkdown((<TextExercise>this.exercise).sampleSolution);
                } else if (this.exercise.type === this.ExerciseType_MODELING) {
                    this.modelingExercise = this.exercise as ModelingExercise;
                    if (this.modelingExercise.sampleSolutionModel) {
                        this.formattedSampleSolution = this.artemisMarkdown.htmlForMarkdown(this.modelingExercise.sampleSolutionExplanation);
                        this.exampleSolutionModel = JSON.parse(this.modelingExercise.sampleSolutionModel);
                    }
                }

                this.tutorParticipation = this.exercise.tutorParticipations[0];
                this.tutorParticipationStatus = this.tutorParticipation.status;
                if (this.exercise.exampleSubmissions && this.exercise.exampleSubmissions.length > 0) {
                    this.exampleSubmissionsToReview = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => !exampleSubmission.usedForTutorial);
                    this.exampleSubmissionsToAssess = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => exampleSubmission.usedForTutorial);
                }
                this.exampleSubmissionsCompletedByTutor = this.tutorParticipation.trainedExampleSubmissions || [];

                this.stats.toReview.total = this.exampleSubmissionsToReview.length;
                this.stats.toReview.done = this.exampleSubmissionsCompletedByTutor.filter(e => !e.usedForTutorial).length;
                this.stats.toAssess.total = this.exampleSubmissionsToAssess.length;
                this.stats.toAssess.done = this.exampleSubmissionsCompletedByTutor.filter(e => e.usedForTutorial).length;

                if (this.stats.toReview.done < this.stats.toReview.total) {
                    this.nextExampleSubmissionId = this.exampleSubmissionsToReview[this.stats.toReview.done].id;
                } else if (this.stats.toAssess.done < this.stats.toAssess.total) {
                    this.nextExampleSubmissionId = this.exampleSubmissionsToAssess[this.stats.toAssess.done].id;
                }

                this.getSubmissions();

                // We don't want to assess submissions before the exercise due date
                if (!this.exercise.dueDate || this.exercise.dueDate.isBefore(Date.now())) {
                    this.getSubmissionWithoutAssessment();
                }
            },
            (response: string) => this.onError(response),
        );

        this.complaintService
            .getForTutor(this.exerciseId)
            .subscribe((res: HttpResponse<Complaint[]>) => (this.complaints = res.body), (error: HttpErrorResponse) => this.onError(error.message));

        this.exerciseService.getStatsForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<StatsForTutorDashboard>) => {
                this.numberOfSubmissions = res.body.numberOfSubmissions;
                this.numberOfAssessments = res.body.numberOfAssessments;
                this.numberOfTutorAssessments = res.body.numberOfTutorAssessments;
                this.numberOfComplaints = res.body.numberOfComplaints;
                this.numberOfTutorComplaints = res.body.numberOfTutorComplaints;

                if (this.numberOfSubmissions > 0) {
                    this.totalAssessmentPercentage = Math.round((this.numberOfAssessments / this.numberOfSubmissions) * 100);
                    this.tutorAssessmentPercentage = Math.round((this.numberOfTutorAssessments / this.numberOfSubmissions) * 100);
                }
            },
            (response: string) => this.onError(response),
        );
    }

    // TODO CZ: too much duplicated code
    private getSubmissions(): void {
        if (this.exercise.type === ExerciseType.TEXT) {
            this.textSubmissionService
                .getTextSubmissionsForExercise(this.exerciseId, { assessedByTutor: true })
                .map((response: HttpResponse<TextSubmission[]>) =>
                    response.body.map((submission: TextSubmission) => {
                        if (submission.result) {
                            // reconnect some associations
                            submission.result.submission = submission;
                            submission.result.participation = submission.participation;
                            submission.participation.results = [submission.result];
                        }

                        return submission;
                    }),
                )
                .subscribe((submissions: TextSubmission[]) => {
                    this.submissions = submissions;
                });
        } else if (this.exercise.type === ExerciseType.MODELING) {
            this.modelingSubmissionService
                .getModelingSubmissionsForExercise(this.exerciseId, { assessedByTutor: true })
                .map((response: HttpResponse<ModelingSubmission[]>) =>
                    response.body.map((submission: ModelingSubmission) => {
                        if (submission.result) {
                            // reconnect some associations
                            submission.result.submission = submission;
                            submission.result.participation = submission.participation;
                            submission.participation.results = [submission.result];
                        }

                        return submission;
                    }),
                )
                .subscribe((submissions: ModelingSubmission[]) => {
                    this.submissions = submissions;
                    this.numberOfTutorAssessments = submissions.filter(submission => submission.result.completionDate).length;
                });
        }
    }

    // TODO CZ: too much duplicated code
    private getSubmissionWithoutAssessment(): void {
        if (this.exercise.type === ExerciseType.TEXT) {
            this.textSubmissionService.getTextSubmissionForExerciseWithoutAssessment(this.exerciseId).subscribe(
                (response: HttpResponse<TextSubmission>) => {
                    this.unassessedSubmission = response.body;
                },
                (error: HttpErrorResponse) => {
                    if (error.status === 404) {
                        // there are no unassessed submission, nothing we have to worry about
                    } else {
                        this.onError(error.message);
                    }
                },
            );
        } else if (this.exercise.type === ExerciseType.MODELING) {
            this.modelingSubmissionService.getModelingSubmissionForExerciseWithoutAssessment(this.exerciseId).subscribe(
                (response: ModelingSubmission) => {
                    this.unassessedSubmission = response;
                },
                (error: HttpErrorResponse) => {
                    if (error.status === 404) {
                        // there are no unassessed submission, nothing we have to worry about
                    } else {
                        this.onError(error.message);
                    }
                },
            );
        }
    }

    readInstruction() {
        this.tutorParticipationService.create(this.tutorParticipation, this.exerciseId).subscribe((res: HttpResponse<TutorParticipation>) => {
            this.tutorParticipation = res.body;
            this.tutorParticipationStatus = this.tutorParticipation.status;
            this.jhiAlertService.success('arTeMiSApp.tutorExerciseDashboard.participation.instructionsReviewed');
        }, this.onError);
    }

    hasBeenCompletedByTutor(id: number) {
        return this.exampleSubmissionsCompletedByTutor.filter(e => e.id === id).length > 0;
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, null);
    }

    calculateStatus(submission: Submission) {
        if (submission.result && submission.result.completionDate) {
            return 'DONE';
        }

        return 'DRAFT';
    }

    openExampleSubmission(submissionId: number, readOnly?: boolean, toComplete?: boolean) {
        if (!this.exercise || !this.exercise.type || !submissionId) {
            return;
        }
        const route = `/${this.exercise.type}-exercise/${this.exercise.id}/example-submission/${submissionId}`;
        // TODO CZ: add both flags and check for value in example submission components
        const queryParams: ExampleSubmissionQueryParams = {};
        if (readOnly) {
            queryParams.readOnly = readOnly;
        }
        if (toComplete) {
            queryParams.toComplete = toComplete;
        }

        this.router.navigate([route], { queryParams });
    }

    openAssessmentEditor(submissionId: number, isNewAssessment = false) {
        if (!this.exercise || !this.exercise.type || !submissionId) {
            return;
        }

        const queryParams: any = {};
        let route: string;
        let submission = submissionId.toString();
        if (isNewAssessment) {
            submission = 'new';
        }

        if (this.exercise.type === ExerciseType.TEXT) {
            route = `/text/${this.exercise.id}/assessment/${submission}`;
        } else if (this.exercise.type === ExerciseType.MODELING) {
            route = `/modeling-exercise/${this.exercise.id}/submissions/${submission}/assessment`;
            queryParams.showBackButton = true;
        }
        this.router.navigate([route], { queryParams });
    }

    back() {
        this.router.navigate([`/course/${this.courseId}/tutor-dashboard`]);
    }

    calculateComplaintStatus(accepted?: boolean) {
        if (accepted !== undefined) {
            return 'The complaint has already been evaluated';
        }
        // in the case of 'undefined' the complaint is not yet handled
        return 'The complaint still needs to be evaluated';
    }
}
