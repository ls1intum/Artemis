import { Component, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { User } from 'app/core/user/user.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { UMLModel } from '@ls1intum/apollon';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { getLatestSubmissionResult, getSubmissionResultByCorrectionRound, setLatestSubmissionResult, Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { TranslateService } from '@ngx-translate/core';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { Exam } from 'app/entities/exam.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercises/shared/submission/submission.service';
import { Result } from 'app/entities/result.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { ExerciseView, isOrion, OrionState } from 'app/shared/orion/orion';
import { onError } from 'app/shared/util/global.utils';
import { round } from 'app/shared/util/utils';
import { getExerciseSubmissionsLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { AssessmentType } from 'app/entities/assessment-type.model';

export interface ExampleSubmissionQueryParams {
    readOnly?: boolean;
    toComplete?: boolean;
}

@Component({
    selector: 'jhi-courses',
    templateUrl: './exercise-assessment-dashboard.component.html',
    styles: ['jhi-collapsable-assessment-instructions { max-height: 100vh }'],
    providers: [CourseManagementService],
})
export class ExerciseAssessmentDashboardComponent implements OnInit {
    readonly round = round;
    readonly ExerciseView = ExerciseView;
    readonly assessmentType = AssessmentType;
    exercise: Exercise;
    modelingExercise: ModelingExercise;
    programmingExercise: ProgrammingExercise;
    courseId: number;
    exam?: Exam;
    examId: number;
    exerciseGroupId: number;
    isExamMode = false;
    isTestRun = false;
    isAtLeastInstructor = false;

    statsForDashboard = new StatsForDashboard();

    exerciseId: number;
    numberOfTutorAssessments = 0;
    numberOfSubmissions = new DueDateStat();
    totalNumberOfAssessments = new DueDateStat();
    numberOfAssessmentsOfCorrectionRounds = [new DueDateStat()];
    numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = [new DueDateStat()];
    numberOfComplaints = 0;
    numberOfOpenComplaints = 0;
    numberOfTutorComplaints = 0;
    numberOfMoreFeedbackRequests = 0;
    numberOfOpenMoreFeedbackRequests = 0;
    numberOfTutorMoreFeedbackRequests = 0;
    complaintsEnabled = false;
    feedbackRequestEnabled = false;
    totalAssessmentPercentage = new DueDateStat();
    tutorAssessmentPercentage = 0;
    tutorParticipationStatus: TutorParticipationStatus;
    submissionsByCorrectionRound: Map<number, Submission[]> = new Map<number, Submission[]>();
    unassessedSubmissionByCorrectionRound?: Map<number, Submission> = new Map<number, Submission>();
    exampleSubmissionsToReview: ExampleSubmission[] = [];
    exampleSubmissionsToAssess: ExampleSubmission[] = [];
    exampleSubmissionsCompletedByTutor: ExampleSubmission[] = [];
    tutorParticipation: TutorParticipation;
    nextExampleSubmissionId: number;
    exampleSolutionModel: UMLModel;
    complaints: Complaint[] = [];
    moreFeedbackRequests: Complaint[] = [];
    submissionsWithComplaints: SubmissionWithComplaintDTO[] = [];
    submissionLockLimitReached = false;
    openingAssessmentEditorForNewSubmission = false;
    secondCorrectionEnabled = false;
    numberOfCorrectionRoundsEnabled = this.secondCorrectionEnabled ? 2 : 1;

    formattedGradingInstructions?: SafeHtml;
    formattedProblemStatement?: SafeHtml;
    formattedSampleSolution?: SafeHtml;
    getSubmissionResultByCorrectionRound = getSubmissionResultByCorrectionRound;

    // helper variables to display information message about why no new assessments are possible anymore
    remainingAssessments: number[] = [];
    lockedSubmissionsByOtherTutor: number[] = [];
    notYetAssessed: number[] = [];
    firstRoundAssessments: number;

    readonly ExerciseType = ExerciseType;

    orionState: OrionState;
    isOrionAndProgramming = false;

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

    tutor?: User;
    togglingSecondCorrectionButton = false;

    exerciseForGuidedTour?: Exercise;

    constructor(
        private exerciseService: ExerciseService,
        private courseManagementService: CourseManagementService,
        private jhiAlertService: JhiAlertService,
        private translateService: TranslateService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private tutorParticipationService: TutorParticipationService,
        private submissionService: SubmissionService,
        private textSubmissionService: TextSubmissionService,
        private modelingSubmissionService: ModelingSubmissionService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private artemisMarkdown: ArtemisMarkdownService,
        private router: Router,
        private complaintService: ComplaintService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private modalService: NgbModal,
        private guidedTourService: GuidedTourService,
        private artemisDatePipe: ArtemisDatePipe,
        private sortService: SortService,
        private orionConnectorService: OrionConnectorService,
    ) {}

    /**
     * Extracts the course and exercise ids from the route params and fetches the exercise from the server
     */
    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.isTestRun = this.router.url.indexOf('test-assessment-dashboard') >= 0;
        this.unassessedSubmissionByCorrectionRound = new Map<number, Submission>();

        if (this.route.snapshot.paramMap.has('examId')) {
            this.examId = Number(this.route.snapshot.paramMap.get('examId'));
            this.exerciseGroupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        }

        this.orionConnectorService.state().subscribe((state) => {
            this.orionState = state;
        });

        this.loadAll();
        this.accountService.identity().then((user: User) => (this.tutor = user));
    }

    /**
     * Loads all information from the server regarding this exercise that is needed for the tutor exercise dashboard
     */
    loadAll() {
        this.exerciseService.getForTutors(this.exerciseId).subscribe(
            (res: HttpResponse<Exercise>) => {
                this.exercise = res.body!;
                this.secondCorrectionEnabled = this.exercise.secondCorrectionEnabled;
                this.numberOfCorrectionRoundsEnabled = this.secondCorrectionEnabled ? 2 : 1;
                this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.gradingInstructions);
                this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.problemStatement);
                this.isAtLeastInstructor = this.accountService.isAtLeastInstructorForExercise(this.exercise);

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
                    case ExerciseType.PROGRAMMING:
                        this.programmingExercise = this.exercise as ProgrammingExercise;
                        break;
                }

                this.exerciseForGuidedTour = this.guidedTourService.enableTourForExercise(this.exercise, tutorAssessmentTour, false);
                this.tutorParticipation = this.exercise.tutorParticipations![0];
                this.tutorParticipationStatus = this.tutorParticipation.status!;
                if (this.exercise.exampleSubmissions && this.exercise.exampleSubmissions.length > 0) {
                    this.exampleSubmissionsToReview = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => !exampleSubmission.usedForTutorial);
                    this.exampleSubmissionsToAssess = this.exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => exampleSubmission.usedForTutorial);
                }
                this.exampleSubmissionsCompletedByTutor = this.tutorParticipation.trainedExampleSubmissions || [];

                this.stats.toReview.total = this.exampleSubmissionsToReview.length;
                this.stats.toReview.done = this.exampleSubmissionsCompletedByTutor.filter((e) => !e.usedForTutorial).length;
                this.stats.toAssess.total = this.exampleSubmissionsToAssess.length;
                this.stats.toAssess.done = this.exampleSubmissionsCompletedByTutor.filter((e) => e.usedForTutorial).length;

                if (this.stats.toReview.done < this.stats.toReview.total) {
                    this.nextExampleSubmissionId = this.exampleSubmissionsToReview[this.stats.toReview.done].id!;
                } else if (this.stats.toAssess.done < this.stats.toAssess.total) {
                    this.nextExampleSubmissionId = this.exampleSubmissionsToAssess[this.stats.toAssess.done].id!;
                }

                // exercise belongs to an exam
                if (this.exercise?.exerciseGroup) {
                    this.isExamMode = true;
                    this.exam = this.exercise?.exerciseGroup?.exam;
                    this.secondCorrectionEnabled = this.exercise?.secondCorrectionEnabled;
                }
                this.getAllTutorAssessedSubmissionsForAllCorrectionRounds();

                // 1. We don't want to assess submissions before the exercise due date
                // 2. The assessment for team exercises is not started from the tutor exercise dashboard but from the team pages
                // 3. Don't handle test run submissions here
                if ((!this.exercise.dueDate || this.exercise.dueDate.isBefore(Date.now())) && !this.exercise.teamMode && !this.isTestRun) {
                    this.getSubmissionWithoutAssessmentForAllCorrectionRounds();
                }

                this.isOrionAndProgramming = isOrion && this.exercise.type === ExerciseType.PROGRAMMING;

                // load the guided tour step only after everything else on the page is loaded
                this.guidedTourService.componentPageLoaded();
            },
            (response: string) => this.onError(response),
        );

        if (!this.isTestRun) {
            this.submissionService.getSubmissionsWithComplaintsForTutor(this.exerciseId).subscribe(
                (res: HttpResponse<SubmissionWithComplaintDTO[]>) => {
                    this.submissionsWithComplaints = res.body || [];
                },
                (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
            );

            this.complaintService.getMoreFeedbackRequestsForTutor(this.exerciseId).subscribe(
                (res: HttpResponse<Complaint[]>) => (this.moreFeedbackRequests = res.body as Complaint[]),
                (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
            );

            this.exerciseService.getStatsForTutors(this.exerciseId).subscribe(
                (res: HttpResponse<StatsForDashboard>) => {
                    this.statsForDashboard = StatsForDashboard.from(res.body!);
                    this.numberOfSubmissions = this.statsForDashboard.numberOfSubmissions;
                    this.totalNumberOfAssessments = this.statsForDashboard.totalNumberOfAssessments;
                    this.numberOfAssessmentsOfCorrectionRounds = this.statsForDashboard.numberOfAssessmentsOfCorrectionRounds;
                    this.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = this.statsForDashboard.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound;

                    this.numberOfComplaints = this.statsForDashboard.numberOfComplaints;
                    this.numberOfOpenComplaints = this.statsForDashboard.numberOfOpenComplaints;
                    this.numberOfMoreFeedbackRequests = this.statsForDashboard.numberOfMoreFeedbackRequests;
                    this.numberOfOpenMoreFeedbackRequests = this.statsForDashboard.numberOfOpenMoreFeedbackRequests;
                    const tutorLeaderboardEntry = this.statsForDashboard.tutorLeaderboardEntries?.find((entry) => entry.userId === this.tutor!.id);
                    this.sortService.sortByProperty(this.statsForDashboard.tutorLeaderboardEntries, 'points', false);
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;
                        this.numberOfTutorComplaints = tutorLeaderboardEntry.numberOfTutorComplaints;
                        this.numberOfTutorMoreFeedbackRequests = tutorLeaderboardEntry.numberOfTutorMoreFeedbackRequests;
                    } else {
                        this.numberOfTutorAssessments = 0;
                        this.numberOfTutorComplaints = 0;
                        this.numberOfTutorMoreFeedbackRequests = 0;
                    }

                    if (this.numberOfSubmissions.inTime > 0) {
                        this.totalAssessmentPercentage.inTime = Math.floor((this.totalNumberOfAssessments.inTime / this.numberOfSubmissions.inTime) * 100);
                    } else {
                        this.totalAssessmentPercentage.inTime = 100;
                    }
                    if (this.numberOfSubmissions.late > 0) {
                        this.totalAssessmentPercentage.late = Math.floor((this.totalNumberOfAssessments.late / this.numberOfSubmissions.late) * 100);
                    } else {
                        this.totalAssessmentPercentage.late = 100;
                    }
                    if (this.numberOfSubmissions.total > 0) {
                        this.tutorAssessmentPercentage = Math.floor((this.numberOfTutorAssessments / this.numberOfSubmissions.total) * 100);
                    } else {
                        this.tutorAssessmentPercentage = 100;
                    }
                    this.complaintsEnabled = this.statsForDashboard.complaintsEnabled;
                    this.feedbackRequestEnabled = this.statsForDashboard.feedbackRequestEnabled;
                    this.calculateAssessmentProgressInformation();
                },
                (response: string) => this.onError(response),
            );
        } else {
            this.complaintService.getComplaintsForTestRun(this.exerciseId).subscribe(
                (res: HttpResponse<Complaint[]>) => (this.complaints = res.body as Complaint[]),
                (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
            );
        }
    }

    language(submission: Submission): string {
        if (submission.submissionExerciseType === SubmissionExerciseType.TEXT) {
            return (submission as TextSubmission).language || 'UNKNOWN';
        }
        return 'UNKNOWN';
    }

    /**
     * get all submissions for all correction rounds which the tutor has assessed.
     * If not in examMode, correction rounds defaults to 0, as more than 1 is currently not supported.
     * @private
     */
    private getAllTutorAssessedSubmissionsForAllCorrectionRounds(): void {
        if (this.isExamMode) {
            for (let i = 0; i < this.exam!.numberOfCorrectionRoundsInExam!; i++) {
                this.getAllTutorAssessedSubmissionsForCorrectionRound(i);
            }
        } else {
            this.getAllTutorAssessedSubmissionsForCorrectionRound(0);
        }
    }

    /**
     * Get all the submissions from the server for which the current user is the assessor for the specified correction round,
     * which is the case for started or completed assessments. All these submissions get listed
     * in the exercise dashboard.
     */
    private getAllTutorAssessedSubmissionsForCorrectionRound(correctionRound: number): void {
        let submissionsObservable: Observable<HttpResponse<Submission[]>> = of();
        if (this.isTestRun) {
            submissionsObservable = this.submissionService.getTestRunSubmissionsForExercise(this.exerciseId);
        } else {
            // TODO: This could be one generic endpoint.
            switch (this.exercise.type) {
                case ExerciseType.TEXT:
                    submissionsObservable = this.textSubmissionService.getTextSubmissionsForExerciseByCorrectionRound(this.exerciseId, { assessedByTutor: true }, correctionRound);
                    break;
                case ExerciseType.MODELING:
                    submissionsObservable = this.modelingSubmissionService.getModelingSubmissionsForExerciseByCorrectionRound(
                        this.exerciseId,
                        { assessedByTutor: true },
                        correctionRound,
                    );
                    break;
                case ExerciseType.FILE_UPLOAD:
                    submissionsObservable = this.fileUploadSubmissionService.getFileUploadSubmissionsForExerciseByCorrectionRound(
                        this.exerciseId,
                        { assessedByTutor: true },
                        correctionRound,
                    );
                    break;
                case ExerciseType.PROGRAMMING:
                    submissionsObservable = this.programmingSubmissionService.getProgrammingSubmissionsForExerciseByCorrectionRound(
                        this.exerciseId,
                        { assessedByTutor: true },
                        correctionRound,
                    );
                    break;
            }
        }

        submissionsObservable
            .pipe(
                map((res) => res.body),
                map(this.reconnectEntities),
            )
            .subscribe((submissions: Submission[]) => {
                // Set the received submissions. As the result component depends on the submission we nest it into the participation.
                const sub = submissions
                    .filter((submission) => {
                        return submission?.results && submission.results.length > correctionRound && submission.results[correctionRound];
                    })
                    .map((submission) => {
                        submission.participation!.submissions = [submission];
                        submission.participation!.results = submission.results;
                        setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
                        return submission;
                    });

                this.submissionsByCorrectionRound!.set(correctionRound, sub);
            });
    }

    /**
     * Reconnect submission, result and participation for all submissions in the given array.
     */
    private reconnectEntities = (submissions: Submission[]) => {
        return submissions.map((submission: Submission) => {
            const latestResult = getLatestSubmissionResult(submission);
            if (latestResult) {
                // reconnect some associations
                latestResult!.submission = submission;
                latestResult!.participation = submission.participation;
                submission.participation!.results = [latestResult!];
                setLatestSubmissionResult(submission, latestResult);
            }
            return submission;
        });
    };

    /**
     * Get all submissions that dont have an assessment for all correction rounds
     * If not in examMode correction rounds defaults to 0.
     * @private
     */
    private getSubmissionWithoutAssessmentForAllCorrectionRounds(): void {
        if (this.isExamMode) {
            for (let i = 0; i < this.exam!.numberOfCorrectionRoundsInExam!; i++) {
                if (i <= this.numberOfCorrectionRoundsEnabled) {
                    this.getSubmissionWithoutAssessmentForCorrectionRound(i);
                }
            }
        } else {
            this.getSubmissionWithoutAssessmentForCorrectionRound(0);
        }
    }

    /**
     * Get a submission from the server that does not have an assessment for the given correction round yet (if there is one).
     * The submission gets added to the end of the list of submissions in the exercise
     * dashboard and the user can start the assessment. Note, that the number of started but unfinished assessments is limited per user and course.
     * If the user reached this limit,
     * the server will respond with a BAD REQUEST response here.
     */
    private getSubmissionWithoutAssessmentForCorrectionRound(correctionRound: number): void {
        let submissionObservable: Observable<Submission> = of();
        switch (this.exercise.type) {
            case ExerciseType.TEXT:
                submissionObservable = this.textSubmissionService.getTextSubmissionForExerciseForCorrectionRoundWithoutAssessment(this.exerciseId, 'head', correctionRound);
                break;
            case ExerciseType.MODELING:
                submissionObservable = this.modelingSubmissionService.getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment(
                    this.exerciseId,
                    undefined,
                    correctionRound,
                );
                break;
            case ExerciseType.FILE_UPLOAD:
                submissionObservable = this.fileUploadSubmissionService.getFileUploadSubmissionForExerciseForCorrectionRoundWithoutAssessment(
                    this.exerciseId,
                    undefined,
                    correctionRound,
                );
                break;
            case ExerciseType.PROGRAMMING:
                submissionObservable = this.programmingSubmissionService.getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(
                    this.exerciseId,
                    undefined,
                    correctionRound,
                );
                break;
        }

        submissionObservable.subscribe(
            (submission: Submission) => {
                if (submission) {
                    setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
                    this.unassessedSubmissionByCorrectionRound!.set(correctionRound, submission);
                }
                this.submissionLockLimitReached = false;
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    if (this.unassessedSubmissionByCorrectionRound) {
                        this.unassessedSubmissionByCorrectionRound.delete(correctionRound);
                    }
                } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.submissionLockLimitReached = true;
                } else {
                    this.onError(error?.error?.detail || error.message);
                }
            },
        );
    }

    /**
     * Called after the tutor has read the instructions and creates a new tutor participation
     */
    readInstruction() {
        this.tutorParticipationService.create(this.tutorParticipation, this.exerciseId).subscribe((res: HttpResponse<TutorParticipation>) => {
            this.tutorParticipation = res.body!;
            this.tutorParticipationStatus = this.tutorParticipation.status!;
            this.jhiAlertService.success('artemisApp.exerciseAssessmentDashboard.participation.instructionsReviewed');
        }, this.onError);
    }

    /**
     * Returns whether the example submission for the given id has already been completed
     * @param exampleSubmissionId Id of the example submission which to check for completion
     */
    hasBeenCompletedByTutor(exampleSubmissionId: number) {
        return this.exampleSubmissionsCompletedByTutor.filter((exampleSubmission) => exampleSubmission.id === exampleSubmissionId).length > 0;
    }

    private onError(error: string) {
        this.jhiAlertService.error(error);
    }

    /**
     * Calculates the status of a submission by inspecting the result
     * @param submission Submission which to check
     * @param correctionRound for which to get status
     */
    calculateSubmissionStatus(submission: Submission, correctionRound = 0) {
        const tmpResult = submission.results?.[correctionRound];
        if (tmpResult && tmpResult!.completionDate && Result.isManualResult(tmpResult!)) {
            return 'DONE';
        }
        return 'DRAFT';
    }

    calculateComplaintStatus(complaint: Complaint) {
        // a complaint is handled if it is either accepted or denied and a complaint response exists
        const handled = complaint.accepted !== undefined && complaint.complaintResponse !== undefined;
        if (handled) {
            return this.translateService.instant('artemisApp.exerciseAssessmentDashboard.complaintEvaluated');
        } else {
            if (this.complaintService.isComplaintLocked(complaint)) {
                if (this.complaintService.isComplaintLockedByLoggedInUser(complaint)) {
                    const endDate = this.artemisDatePipe.transform(complaint.complaintResponse?.lockEndDate);
                    return this.translateService.instant('artemisApp.locks.lockInformationYou', {
                        endDate,
                    });
                } else {
                    const endDate = this.artemisDatePipe.transform(complaint.complaintResponse?.lockEndDate);
                    const user = complaint.complaintResponse?.reviewer?.login;

                    return this.translateService.instant('artemisApp.locks.lockInformation', {
                        endDate,
                        user,
                    });
                }
            } else {
                return this.translateService.instant('artemisApp.exerciseAssessmentDashboard.complaintNotEvaluated');
            }
        }
    }

    /**
     * Uses the router to navigate to a given example submission
     * @param submissionId Id of submission where to navigate to
     * @param readOnly Flag whether the view should be opened in read-only mode
     * @param toComplete Flag whether the view should be opened in to-complete mode
     */
    openExampleSubmission(submissionId: number, readOnly?: boolean, toComplete?: boolean) {
        if (!this.exercise || !this.exercise.type || !submissionId) {
            return;
        }
        const route = `/course-management/${this.courseId}/${this.exercise.type}-exercises/${this.exercise.id}/example-submissions/${submissionId}`;
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

    isComplaintLocked(complaint: Complaint) {
        return this.complaintService.isComplaintLockedForLoggedInUser(complaint, this.exercise);
    }

    /**
     * Uses the router to navigate to the assessment editor for a given/new submission
     * @param submission Either submission or 'new'.
     * @param correctionRound
     */
    async openAssessmentEditor(submission: Submission | 'new', correctionRound = 0): Promise<void> {
        if (!this.exercise || !this.exercise.type || !submission) {
            return;
        }

        this.openingAssessmentEditorForNewSubmission = true;
        const submissionId: number | 'new' = submission === 'new' ? 'new' : submission.id!;
        let participationId = undefined;
        if (submission !== 'new' && submission.participation !== undefined) {
            participationId = submission.participation!.id;
        }
        const url = getLinkToSubmissionAssessment(this.exercise.type!, this.courseId, this.exerciseId, participationId, submissionId, this.examId, this.exerciseGroupId);
        if (this.isTestRun) {
            await this.router.navigate(url, { queryParams: { testRun: this.isTestRun, 'correction-round': correctionRound } });
        } else {
            await this.router.navigate(url, { queryParams: { 'correction-round': correctionRound } });
        }
        this.openingAssessmentEditorForNewSubmission = false;
    }

    /**
     * Triggers downloading the test repository and opening it, allowing for submissions to be downloaded
     */
    openAssessmentInOrion() {
        this.orionConnectorService.assessExercise(this.exercise);
    }

    /**
     * Retrieves a new submission if necessary and then delegates to the
     * {@link programmingSubmissionService} to download the submission
     *
     * @param submission submission to send to Orion or 'new' if a new one should be loaded
     * @param correctionRound correction round
     */
    downloadSubmissionInOrion(submission: Submission | 'new', correctionRound = 0) {
        if (submission === 'new') {
            this.programmingSubmissionService
                .getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(this.exerciseId, true, correctionRound)
                .subscribe((newSubmission) => this.programmingSubmissionService.downloadSubmissionInOrion(this.exerciseId, newSubmission.id!, correctionRound));
        } else {
            this.programmingSubmissionService.downloadSubmissionInOrion(this.exerciseId, submission.id!, correctionRound);
        }
    }

    /**
     * Show complaint depending on the exercise type
     * @param complaint that we want to show
     */
    viewComplaint(complaint: Complaint) {
        const submission: Submission = complaint.result?.submission!;
        // numberOfAssessmentsOfCorrectionRounds size is the number of correction rounds
        if (complaint.complaintType === ComplaintType.MORE_FEEDBACK) {
            this.openAssessmentEditor(submission, this.numberOfAssessmentsOfCorrectionRounds.length - 1);
        }
        const submissionToView = this.submissionsWithComplaints.filter((dto) => dto.submission.id === submission.id).pop()?.submission;
        if (submissionToView) {
            if (!submissionToView.results) {
                submissionToView.results = [];
            }
            submissionToView.results = submissionToView.results?.filter((result) => result.assessmentType !== AssessmentType.AUTOMATIC);
            this.openAssessmentEditor(submissionToView, submissionToView.results!.length - 1);
        }
    }

    toggleSecondCorrection() {
        this.togglingSecondCorrectionButton = true;
        this.exerciseService.toggleSecondCorrection(this.exerciseId).subscribe((res: Boolean) => {
            this.secondCorrectionEnabled = res as boolean;
            this.numberOfCorrectionRoundsEnabled = this.secondCorrectionEnabled ? 2 : 1;
            this.getSubmissionWithoutAssessmentForAllCorrectionRounds();
            this.togglingSecondCorrectionButton = false;
        });
    }

    getSubmissionsLinkForExercise(exercise: Exercise): string[] {
        return getExerciseSubmissionsLink(exercise.type!, this.courseId, exercise.id!, this.examId, this.exerciseGroupId);
    }

    /**
     * To correctly display why a tutor cannot assess any further submissions we need to calculate those values.
     */
    calculateAssessmentProgressInformation() {
        if (this.exam) {
            for (let i = 0; i < (this.exam.numberOfCorrectionRoundsInExam ?? 0); i++) {
                this.lockedSubmissionsByOtherTutor[i] = this.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound[i]?.inTime;

                // number of submissions in the particular round that still need assessment and are not locked
                this.notYetAssessed[i] = this.numberOfSubmissions.inTime - this.numberOfAssessmentsOfCorrectionRounds[i].inTime - this.lockedSubmissionsByOtherTutor[i];

                // The number of assessments which are still open but cannot be assessed as they were already assessed in the first round.
                // Since if this will be displayed the number of assessments the tutor can create is 0 we can simply get this number by subtracting the
                // lock-count from the remaining unassessed submissions of the current correction round.
                this.firstRoundAssessments = this.notYetAssessed[i] - this.lockedSubmissionsByOtherTutor[i];
            }
        }
    }
}
