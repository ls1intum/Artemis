import { Component, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseService } from 'app/course/manage/course.service';
import { AlertService } from 'app/core/alert/alert.service';
import { User } from 'app/core/user/user.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TutorParticipationService } from 'app/exercises/shared/tutor-exercise-dashboard/tutor-participation.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission/text-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { ArtemisMarkdown } from 'app/shared/markdown.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { UMLModel } from '@ls1intum/apollon';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Complaint } from 'app/entities/complaint.model';
import { Submission } from 'app/entities/submission.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission/modeling-submission.service';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { StatsForDashboard } from 'app/course/instructor-course-dashboard/stats-for-dashboard.model';
import { TranslateService } from '@ngx-translate/core';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission/file-upload-submission.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission/programming-submission.service';
import { Result } from 'app/entities/result.model';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/exercises/programming/assess/programming-assessment/manual-result/programming-assessment-manual-result-dialog.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { cloneDeep } from 'lodash';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

export interface ExampleSubmissionQueryParams {
    readOnly?: boolean;
    toComplete?: boolean;
}

@Component({
    selector: 'jhi-courses',
    templateUrl: './tutor-exercise-dashboard.component.html',
    styles: ['jhi-collapsable-assessment-instructions { max-height: 100vh }'],
    providers: [CourseService],
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
    numberOfOpenComplaints = 0;
    numberOfTutorComplaints = 0;
    numberOfMoreFeedbackRequests = 0;
    numberOfOpenMoreFeedbackRequests = 0;
    numberOfTutorMoreFeedbackRequests = 0;
    totalAssessmentPercentage = 0;
    tutorAssessmentPercentage = 0;
    tutorParticipationStatus: TutorParticipationStatus;
    submissions: Submission[] = [];
    unassessedSubmission: Submission | null;
    exampleSubmissionsToReview: ExampleSubmission[] = [];
    exampleSubmissionsToAssess: ExampleSubmission[] = [];
    exampleSubmissionsCompletedByTutor: ExampleSubmission[] = [];
    tutorParticipation: TutorParticipation;
    nextExampleSubmissionId: number;
    exampleSolutionModel: UMLModel;
    complaints: Complaint[] = [];
    moreFeedbackRequests: Complaint[] = [];
    submissionLockLimitReached = false;

    formattedGradingInstructions: SafeHtml | null;
    formattedProblemStatement: SafeHtml | null;
    formattedSampleSolution: SafeHtml | null;

    readonly ExerciseType = ExerciseType;

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

    exerciseForGuidedTour: Exercise | null;

    constructor(
        private exerciseService: ExerciseService,
        private jhiAlertService: AlertService,
        private translateService: TranslateService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private tutorParticipationService: TutorParticipationService,
        private textSubmissionService: TextSubmissionService,
        private modelingSubmissionService: ModelingSubmissionService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private artemisMarkdown: ArtemisMarkdown,
        private router: Router,
        private complaintService: ComplaintService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private modalService: NgbModal,
        private guidedTourService: GuidedTourService,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));

        this.loadAll();

        this.accountService.identity().then((user: User) => (this.tutor = user));
    }

    loadAll() {
        this.exerciseService.getForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => {
                this.exercise = res.body!;
                this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.gradingInstructions);
                this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.problemStatement);

                switch (this.exercise.type) {
                    case ExerciseType.TEXT:
                        const textExercise = this.exercise as TextExercise;
                        this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(textExercise.sampleSolution);
                        break;
                    case ExerciseType.MODELING:
                        this.modelingExercise = this.exercise as ModelingExercise;
                        if (this.modelingExercise.sampleSolutionModel) {
                            this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.sampleSolutionExplanation);
                            this.exampleSolutionModel = JSON.parse(this.modelingExercise.sampleSolutionModel);
                        }
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        const fileUploadExercise = this.exercise as FileUploadExercise;
                        this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(fileUploadExercise.sampleSolution);
                        break;
                }

                this.exerciseForGuidedTour = this.guidedTourService.enableTourForExercise(this.exercise, tutorAssessmentTour, false);
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

        this.complaintService.getComplaintsForTutor(this.exerciseId).subscribe(
            (res: HttpResponse<Complaint[]>) => (this.complaints = res.body as Complaint[]),
            (error: HttpErrorResponse) => this.onError(error.message),
        );
        this.complaintService.getMoreFeedbackRequestsForTutor(this.exerciseId).subscribe(
            (res: HttpResponse<Complaint[]>) => (this.moreFeedbackRequests = res.body as Complaint[]),
            (error: HttpErrorResponse) => this.onError(error.message),
        );

        this.exerciseService.getStatsForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<StatsForDashboard>) => {
                this.statsForDashboard = res.body!;
                this.numberOfSubmissions = this.statsForDashboard.numberOfSubmissions;
                this.numberOfAssessments = this.statsForDashboard.numberOfAssessments;
                this.numberOfComplaints = this.statsForDashboard.numberOfComplaints;
                this.numberOfOpenComplaints = this.statsForDashboard.numberOfOpenComplaints;
                this.numberOfMoreFeedbackRequests = this.statsForDashboard.numberOfMoreFeedbackRequests;
                this.numberOfOpenMoreFeedbackRequests = this.statsForDashboard.numberOfOpenMoreFeedbackRequests;
                const tutorLeaderboardEntry = this.statsForDashboard.tutorLeaderboardEntries.find(entry => entry.userId === this.tutor!.id);
                if (tutorLeaderboardEntry) {
                    this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;
                    this.numberOfTutorComplaints = tutorLeaderboardEntry.numberOfAcceptedComplaints;
                    this.numberOfTutorMoreFeedbackRequests = tutorLeaderboardEntry.numberOfNotAnsweredMoreFeedbackRequests;
                } else {
                    this.numberOfTutorAssessments = 0;
                    this.numberOfTutorComplaints = 0;
                    this.numberOfTutorMoreFeedbackRequests = 0;
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
        // TODO: This could be one generic endpoint.
        switch (this.exercise.type) {
            case ExerciseType.TEXT:
                submissionsObservable = this.textSubmissionService.getTextSubmissionsForExercise(this.exerciseId, { assessedByTutor: true });
                break;
            case ExerciseType.MODELING:
                submissionsObservable = this.modelingSubmissionService.getModelingSubmissionsForExercise(this.exerciseId, { assessedByTutor: true });
                break;
            case ExerciseType.FILE_UPLOAD:
                submissionsObservable = this.fileUploadSubmissionService.getFileUploadSubmissionsForExercise(this.exerciseId, { assessedByTutor: true });
                break;
            case ExerciseType.PROGRAMMING:
                submissionsObservable = this.programmingSubmissionService.getProgrammingSubmissionsForExercise(this.exerciseId, { assessedByTutor: true });
                break;
        }

        submissionsObservable
            .pipe(
                map(res => res.body),
                map(this.reconnectEntities),
            )
            .subscribe((submissions: Submission[]) => {
                // Set the received submissions. As the result component depends on the submission we nest it into the participation.
                this.submissions = submissions.map(submission => {
                    submission.participation.submissions = [submission];
                    return submission;
                });
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
        switch (this.exercise.type) {
            case ExerciseType.TEXT:
                submissionObservable = this.textSubmissionService.getTextSubmissionForExerciseWithoutAssessment(this.exerciseId);
                break;
            case ExerciseType.MODELING:
                submissionObservable = this.modelingSubmissionService.getModelingSubmissionForExerciseWithoutAssessment(this.exerciseId);
                break;
            case ExerciseType.FILE_UPLOAD:
                submissionObservable = this.fileUploadSubmissionService.getFileUploadSubmissionForExerciseWithoutAssessment(this.exerciseId);
                break;
            case ExerciseType.PROGRAMMING:
                submissionObservable = this.programmingSubmissionService.getProgrammingSubmissionForExerciseWithoutAssessment(this.exerciseId);
                break;
        }

        submissionObservable.subscribe(
            (submission: Submission) => {
                this.unassessedSubmission = submission;
                this.submissionLockLimitReached = false;
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    this.unassessedSubmission = null;
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

        let route = '';
        let submission = submissionId.toString();
        if (isNewAssessment) {
            submission = 'new';
        }

        switch (this.exercise.type) {
            case ExerciseType.TEXT:
                route = `/text/${this.exercise.id}/assessment/${submission}`;
                break;
            case ExerciseType.MODELING:
                route = `/modeling-exercise/${this.exercise.id}/submissions/${submission}/assessment`;
                break;
            case ExerciseType.FILE_UPLOAD:
                route = `/file-upload-exercise/${this.exercise.id}/submission/${submission}/assessment`;
                break;
        }
        this.router.navigate([route]);
    }

    private openManualResultDialog(result: Result) {
        const modalRef: NgbModalRef = this.modalService.open(ProgrammingAssessmentManualResultDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.participationId = result.participation!.id;
        modalRef.componentInstance.result = cloneDeep(result);
        modalRef.componentInstance.exercise = this.exercise;
        modalRef.componentInstance.onResultModified.subscribe(() => this.loadAll());
        modalRef.result.then(
            _ => this.loadAll(),
            () => {},
        );
        return;
    }

    /**
     * Show complaint depending on the exercise type
     * @param complaint that we want to show
     */
    viewComplaint(complaint: Complaint) {
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.openManualResultDialog(complaint.result);
        } else {
            this.openAssessmentEditor(complaint.result.submission!.id, false);
        }
    }

    asProgrammingExercise(exercise: Exercise) {
        return exercise as ProgrammingExercise;
    }

    back() {
        this.router.navigate([`/course/${this.courseId}/tutor-dashboard`]);
    }
}
