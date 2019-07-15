import { Component, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseService } from '../entities/course';
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
import { Submission, SubmissionExerciseType } from 'app/entities/submission';
import { ModelingSubmissionService } from 'app/entities/modeling-submission';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { StatsForDashboard } from 'app/instructor-course-dashboard/stats-for-dashboard.model';

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

    statsForDashboard = new StatsForDashboard();

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
    submissionLockLimitReached = false;

    formattedGradingInstructions: SafeHtml | null;
    formattedProblemStatement: SafeHtml | null;
    formattedSampleSolution: SafeHtml | null;

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

    tutor: User | null;

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
                this.exercise = res.body!;
                this.formattedGradingInstructions = this.artemisMarkdown.htmlForMarkdown(this.exercise.gradingInstructions);
                this.formattedProblemStatement = this.artemisMarkdown.htmlForMarkdown(this.exercise.problemStatement);

                if (this.exercise.type === this.ExerciseType_TEXT) {
                    const textExercise = this.exercise as TextExercise;
                    this.formattedSampleSolution = this.artemisMarkdown.htmlForMarkdown(textExercise.sampleSolution);
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
            .subscribe((res: HttpResponse<Complaint[]>) => (this.complaints = res.body as Complaint[]), (error: HttpErrorResponse) => this.onError(error.message));

        this.exerciseService.getStatsForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<StatsForDashboard>) => {
                this.statsForDashboard = res.body!;
                this.numberOfSubmissions = this.statsForDashboard.numberOfSubmissions;
                this.numberOfAssessments = this.statsForDashboard.numberOfAssessments;
                this.numberOfComplaints = this.statsForDashboard.numberOfComplaints;
                const tutorLeaderboardEntry = this.statsForDashboard.tutorLeaderboardEntries.find(entry => entry.userId === this.tutor!.id);
                if (tutorLeaderboardEntry) {
                    this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;
                    this.numberOfTutorComplaints = tutorLeaderboardEntry.numberOfAcceptedComplaints;
                } else {
                    this.numberOfTutorAssessments = 0;
                    this.numberOfTutorComplaints = 0;
                }

                if (this.numberOfSubmissions > 0) {
                    this.totalAssessmentPercentage = Math.round((this.numberOfAssessments / this.numberOfSubmissions) * 100);
                    this.tutorAssessmentPercentage = Math.round((this.numberOfTutorAssessments / this.numberOfSubmissions) * 100);
                }
            },
            (response: string) => this.onError(response),
        );
    }

    /**
     * Get all the submissions from the server for which the current user is the assessor, which is the case for started or completed assessments. All these submissions get listed
     * in the exercise dashboard.
     */
    private getSubmissions(): void {
        let submissionsObservable: Observable<HttpResponse<Submission[]>> = of();
        if (this.exercise.type === ExerciseType.TEXT) {
            submissionsObservable = this.textSubmissionService.getTextSubmissionsForExercise(this.exerciseId, { assessedByTutor: true });
        } else if (this.exercise.type === ExerciseType.MODELING) {
            submissionsObservable = this.modelingSubmissionService.getModelingSubmissionsForExercise(this.exerciseId, { assessedByTutor: true });
        }

        submissionsObservable
            .pipe(
                map(res => res.body),
                map(this.reconnectEntities),
            )
            .subscribe((submissions: Submission[]) => {
                this.submissions = submissions;
            });
    }

    /**
     * Reconnect submission, result and participation for all submissions in the given array.
     */
    private reconnectEntities = (submissions: Submission[]) => {
        return submissions.map((submission: Submission) => {
            if (submission.result) {
                // reconnect some associations
                submission.result.submission = submission;
                submission.result.participation = submission.participation;
                submission.participation.results = [submission.result];
            }
            return submission;
        });
    };

    /**
     * Get a submission from the server that does not have an assessment yet (if there is one). The submission gets added to the end of the list of submissions in the exercise
     * dashboard and the user can start the assessment. Note, that the number of started but unfinished assessments is limited per user and course. If the user reached this limit,
     * the server will respond with a BAD REQUEST response here.
     */
    private getSubmissionWithoutAssessment(): void {
        let submissionObservable: Observable<Submission> = of();
        if (this.exercise.type === ExerciseType.TEXT) {
            submissionObservable = this.textSubmissionService.getTextSubmissionForExerciseWithoutAssessment(this.exerciseId);
        } else if (this.exercise.type === ExerciseType.MODELING) {
            submissionObservable = this.modelingSubmissionService.getModelingSubmissionForExerciseWithoutAssessment(this.exerciseId);
        }

        submissionObservable.subscribe(
            (submission: Submission) => {
                this.unassessedSubmission = submission;
                this.submissionLockLimitReached = false;
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.submissionLockLimitReached = true;
                } else {
                    this.onError(error.message);
                }
            },
        );
    }

    readInstruction() {
        this.tutorParticipationService.create(this.tutorParticipation, this.exerciseId).subscribe((res: HttpResponse<TutorParticipation>) => {
            this.tutorParticipation = res.body!;
            this.tutorParticipationStatus = this.tutorParticipation.status;
            this.jhiAlertService.success('artemisApp.tutorExerciseDashboard.participation.instructionsReviewed');
        }, this.onError);
    }

    hasBeenCompletedByTutor(id: number) {
        return this.exampleSubmissionsCompletedByTutor.filter(e => e.id === id).length > 0;
    }

    private onError(error: string) {
        this.jhiAlertService.error(error, null, undefined);
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
        let route = '';
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
