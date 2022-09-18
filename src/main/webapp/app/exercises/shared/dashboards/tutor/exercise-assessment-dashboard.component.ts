import { Component, ContentChild, OnInit, TemplateRef } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AlertService } from 'app/core/util/alert.service';
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
import { finalize, map } from 'rxjs/operators';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { TranslateService } from '@ngx-translate/core';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { Exam } from 'app/entities/exam.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercises/shared/submission/submission.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { onError } from 'app/shared/util/global.utils';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ArtemisNavigationUtilService, getExerciseSubmissionsLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { LegendPosition } from '@swimlane/ngx-charts';
import { AssessmentDashboardInformationEntry } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard-information.component';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { faCheckCircle, faExclamationTriangle, faFolderOpen, faQuestionCircle, faSort, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Authority } from 'app/shared/constants/authority.constants';
import { GraphColors } from 'app/entities/statistics.model';

export interface ExampleSubmissionQueryParams {
    readOnly?: boolean;
    toComplete?: boolean;
}

@Component({
    selector: 'jhi-exercise-assessment-dashboard',
    templateUrl: './exercise-assessment-dashboard.component.html',
    styleUrls: ['./exercise-assessment-dashboard.component.scss'],
    providers: [CourseManagementService],
})
export class ExerciseAssessmentDashboardComponent implements OnInit {
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;
    exercise: Exercise;
    modelingExercise: ModelingExercise;
    programmingExercise: ProgrammingExercise;
    courseId: number;
    exam?: Exam;
    examId: number;
    exerciseGroupId: number;
    isExamMode = false;
    isTestRun = false;
    isLoading = false;

    statsForDashboard = new StatsForDashboard();

    exerciseId: number;
    numberOfTutorAssessments = 0;
    numberOfSubmissions = new DueDateStat();
    totalNumberOfAssessments = new DueDateStat();
    numberOfAutomaticAssistedAssessments = new DueDateStat();
    numberOfAssessmentsOfCorrectionRounds = [new DueDateStat()];
    numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = [new DueDateStat()];
    complaintsEnabled = false;
    feedbackRequestEnabled = false;
    tutorAssessmentPercentage = 0;
    tutorParticipationStatus: TutorParticipationStatus;
    assessedSubmissionsByRound: Map<number, Submission[]> = new Map<number, Submission[]>();
    unassessedSubmissionByRound?: Map<number, Submission> = new Map<number, Submission>();
    exampleSubmissionsToReview: ExampleSubmission[] = [];
    exampleSubmissionsToAssess: ExampleSubmission[] = [];
    exampleSubmissionsCompletedByTutor: ExampleSubmission[] = [];
    tutorParticipation: TutorParticipation;
    nextExampleSubmissionId: number;
    exampleSolutionModel: UMLModel;
    complaints: Complaint[] = [];
    submissionsWithMoreFeedbackRequests: SubmissionWithComplaintDTO[] = [];
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

    // attributes for sorting the tables
    sortPredicates = ['submissionDate', 'complaint.accepted', 'complaint.accepted'];
    reverseOrders = [true, false, false];

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

    tutor?: User;
    togglingSecondCorrectionButton = false;

    exerciseForGuidedTour?: Exercise;

    complaintsDashboardInfo = new AssessmentDashboardInformationEntry(0, 0);
    moreFeedbackRequestsDashboardInfo = new AssessmentDashboardInformationEntry(0, 0);
    ratingsDashboardInfo = new AssessmentDashboardInformationEntry(0, 0);

    // graph
    view: [number, number] = [330, 150];
    legendPosition = LegendPosition.Below;
    assessments: any[];
    customColors: any[];
    isAutomaticAssessedProgrammingExercise = false;

    // links
    submissionsLink: any[];
    complaintsLink: any[];
    moreFeedbackRequestsLink: any[];

    // extension points, see shared/extension-point
    @ContentChild('overrideAssessmentTable') overrideAssessmentTable: TemplateRef<any>;
    @ContentChild('overrideOpenAssessmentButton') overrideOpenAssessmentButton: TemplateRef<any>;

    // Icons
    faSpinner = faSpinner;
    faQuestionCircle = faQuestionCircle;
    faCheckCircle = faCheckCircle;
    faFolderOpen = faFolderOpen;
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;

    constructor(
        public complaintService: ComplaintService,
        private exerciseService: ExerciseService,
        private alertService: AlertService,
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
        private programmingSubmissionService: ProgrammingSubmissionService,
        private guidedTourService: GuidedTourService,
        private artemisDatePipe: ArtemisDatePipe,
        private sortService: SortService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    /**
     * Extracts the course and exercise ids from the route params and fetches the exercise from the server
     */
    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.isTestRun = this.router.url.indexOf('test-assessment-dashboard') >= 0;
        this.unassessedSubmissionByRound = new Map<number, Submission>();

        if (this.route.snapshot.paramMap.has('examId')) {
            this.examId = Number(this.route.snapshot.paramMap.get('examId'));
            this.exerciseGroupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        }

        this.loadAll();
        this.accountService.identity().then((user: User) => (this.tutor = user));
        this.translateService.onLangChange.subscribe(() => {
            this.setupGraph();
        });
    }

    setupGraph() {
        // If the programming exercise is assessed automatically but complaints are enabled, the term "unassessed submissions" might be misleading.
        // In this case, we only show open and resolved complaints
        if (this.programmingExercise && this.programmingExercise.assessmentType === AssessmentType.AUTOMATIC && this.programmingExercise.allowComplaintsForAutomaticAssessments) {
            const numberOfComplaintsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfOpenComplaints');
            const numberOfResolvedComplaintsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfResolvedComplaints');
            this.isAutomaticAssessedProgrammingExercise = true;
            this.customColors = [
                { name: numberOfComplaintsLabel, value: GraphColors.YELLOW },
                { name: numberOfResolvedComplaintsLabel, value: GraphColors.GREEN },
            ];
            this.assessments = [
                {
                    name: numberOfComplaintsLabel,
                    value: this.statsForDashboard.numberOfOpenComplaints,
                },
                {
                    name: numberOfResolvedComplaintsLabel,
                    value: this.statsForDashboard.numberOfComplaints - this.statsForDashboard.numberOfOpenComplaints,
                },
            ];
        } else {
            const numberOfUnassessedSubmissionLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfUnassessedSubmissions');
            const numberOfAutomaticAssistedSubmissionsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfAutomaticAssistedSubmissions');
            const numberOfManualAssessedSubmissionsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfManualAssessedSubmissions');
            this.customColors = [
                { name: numberOfUnassessedSubmissionLabel, value: GraphColors.RED },
                { name: numberOfManualAssessedSubmissionsLabel, value: GraphColors.BLUE },
                { name: numberOfAutomaticAssistedSubmissionsLabel, value: GraphColors.YELLOW },
            ];
            this.assessments = [
                {
                    name: numberOfUnassessedSubmissionLabel,
                    value: this.numberOfSubmissions.total - this.totalNumberOfAssessments.total,
                },
                {
                    name: numberOfManualAssessedSubmissionsLabel,
                    value: this.totalNumberOfAssessments.total - this.numberOfAutomaticAssistedAssessments.total,
                },
                {
                    name: numberOfAutomaticAssistedSubmissionsLabel,
                    value: this.numberOfAutomaticAssistedAssessments.total,
                },
            ];
        }
    }

    setupLinks() {
        this.submissionsLink = ['/course-management', this.courseId, this.exercise.type! + '-exercises', this.exercise.id!, 'submissions'];
        this.complaintsLink = ['/course-management', this.courseId, this.exercise.type! + '-exercises', this.exercise.id!, 'complaints'];
        this.moreFeedbackRequestsLink = ['/course-management', this.courseId, this.exercise.type! + '-exercises', this.exercise.id!, 'more-feedback-requests'];
    }

    /**
     * Loads all information from the server regarding this exercise that is needed for the tutor exercise dashboard
     */
    loadAll() {
        this.exerciseService.getForTutors(this.exerciseId).subscribe({
            next: (res: HttpResponse<Exercise>) => {
                this.exercise = res.body!;
                this.secondCorrectionEnabled = this.exercise.secondCorrectionEnabled;
                this.numberOfCorrectionRoundsEnabled = this.secondCorrectionEnabled ? 2 : 1;
                this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.gradingInstructions);
                this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise.problemStatement);

                switch (this.exercise.type) {
                    case ExerciseType.TEXT:
                        const textExercise = this.exercise as TextExercise;
                        this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(textExercise.exampleSolution);
                        break;
                    case ExerciseType.MODELING:
                        this.modelingExercise = this.exercise as ModelingExercise;
                        if (this.modelingExercise.exampleSolutionModel) {
                            this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(this.modelingExercise.exampleSolutionExplanation);
                            this.exampleSolutionModel = JSON.parse(this.modelingExercise.exampleSolutionModel);
                        }
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        const fileUploadExercise = this.exercise as FileUploadExercise;
                        this.formattedSampleSolution = this.artemisMarkdown.safeHtmlForMarkdown(fileUploadExercise.exampleSolution);
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

                // The assessment for team exercises is not started from the tutor exercise dashboard but from the team pages
                const isAfterDueDate = !this.exercise.dueDate || this.exercise.dueDate.isBefore(dayjs());
                if ((this.exercise.allowManualFeedbackRequests || isAfterDueDate) && !this.exercise.teamMode && !this.isTestRun) {
                    this.getSubmissionWithoutAssessmentForAllCorrectionRounds();
                }

                // load the guided tour step only after everything else on the page is loaded
                this.guidedTourService.componentPageLoaded();

                this.setupLinks();
            },
            error: (response: string) => this.onError(response),
        });

        if (!this.isTestRun) {
            this.submissionService.getSubmissionsWithComplaintsForTutor(this.exerciseId).subscribe({
                next: (res: HttpResponse<SubmissionWithComplaintDTO[]>) => {
                    this.submissionsWithComplaints = res.body || [];
                    this.sortComplaintRows();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });

            this.submissionService.getSubmissionsWithMoreFeedbackRequestsForTutor(this.exerciseId).subscribe({
                next: (res: HttpResponse<SubmissionWithComplaintDTO[]>) => {
                    this.submissionsWithMoreFeedbackRequests = res.body || [];
                    this.sortMoreFeedbackRows();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });

            this.exerciseService.getStatsForTutors(this.exerciseId).subscribe({
                next: (res: HttpResponse<StatsForDashboard>) => {
                    this.statsForDashboard = StatsForDashboard.from(res.body!);
                    this.numberOfSubmissions = this.statsForDashboard.numberOfSubmissions;
                    this.totalNumberOfAssessments = this.statsForDashboard.totalNumberOfAssessments;
                    this.numberOfAssessmentsOfCorrectionRounds = this.statsForDashboard.numberOfAssessmentsOfCorrectionRounds;
                    this.numberOfAutomaticAssistedAssessments = this.statsForDashboard.numberOfAutomaticAssistedAssessments;
                    this.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = this.statsForDashboard.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound;

                    const tutorLeaderboardEntry = this.statsForDashboard.tutorLeaderboardEntries?.find((entry) => entry.userId === this.tutor!.id);
                    this.sortService.sortByProperty(this.statsForDashboard.tutorLeaderboardEntries, 'points', false);

                    // Prepare the table data for the side table
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments = tutorLeaderboardEntry.numberOfAssessments;
                        this.complaintsDashboardInfo = new AssessmentDashboardInformationEntry(
                            this.statsForDashboard.numberOfComplaints,
                            tutorLeaderboardEntry.numberOfTutorComplaints,
                            this.statsForDashboard.numberOfComplaints - this.statsForDashboard.numberOfOpenComplaints,
                        );
                        this.moreFeedbackRequestsDashboardInfo = new AssessmentDashboardInformationEntry(
                            this.statsForDashboard.numberOfMoreFeedbackRequests,
                            tutorLeaderboardEntry.numberOfTutorMoreFeedbackRequests,
                            this.statsForDashboard.numberOfMoreFeedbackRequests - this.statsForDashboard.numberOfOpenMoreFeedbackRequests,
                        );
                        this.ratingsDashboardInfo = new AssessmentDashboardInformationEntry(this.statsForDashboard.numberOfRatings, tutorLeaderboardEntry.numberOfTutorRatings);
                    } else {
                        this.numberOfTutorAssessments = 0;
                        this.complaintsDashboardInfo = new AssessmentDashboardInformationEntry(
                            this.statsForDashboard.numberOfComplaints,
                            0,
                            this.statsForDashboard.numberOfComplaints - this.statsForDashboard.numberOfOpenComplaints,
                        );
                        this.moreFeedbackRequestsDashboardInfo = new AssessmentDashboardInformationEntry(
                            this.statsForDashboard.numberOfMoreFeedbackRequests,
                            0,
                            this.statsForDashboard.numberOfMoreFeedbackRequests - this.statsForDashboard.numberOfOpenMoreFeedbackRequests,
                        );
                        this.ratingsDashboardInfo = new AssessmentDashboardInformationEntry(this.statsForDashboard.numberOfRatings, 0);
                    }

                    if (this.numberOfSubmissions.total > 0) {
                        this.tutorAssessmentPercentage = Math.floor((this.numberOfTutorAssessments / this.numberOfSubmissions.total) * 100);
                    } else {
                        this.tutorAssessmentPercentage = 100;
                    }
                    this.complaintsEnabled = this.statsForDashboard.complaintsEnabled;
                    this.feedbackRequestEnabled = this.statsForDashboard.feedbackRequestEnabled;
                    this.calculateAssessmentProgressInformation();

                    this.setupGraph();
                },
                error: (response: string) => this.onError(response),
            });
        } else {
            this.complaintService.getComplaintsForTestRun(this.exerciseId).subscribe({
                next: (res: HttpResponse<Complaint[]>) => (this.complaints = res.body as Complaint[]),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }

    get yourStatusTitle(): string {
        switch (this.tutorParticipationStatus) {
            case TutorParticipationStatus.TRAINED:
                // If we are in 'TRAINED' state, but never really "trained" on example submissions, display the
                // 'REVIEWED_INSTRUCTIONS' state text instead.
                if (!this.exercise.exampleSubmissions || this.exercise.exampleSubmissions.length === 0) {
                    return TutorParticipationStatus.REVIEWED_INSTRUCTIONS.toString();
                }

                return TutorParticipationStatus.TRAINED.toString();
            default:
                return this.tutorParticipationStatus.toString();
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
                    submissionsObservable = this.textSubmissionService.getSubmissions(this.exerciseId, { assessedByTutor: true }, correctionRound);
                    break;
                case ExerciseType.MODELING:
                    submissionsObservable = this.modelingSubmissionService.getSubmissions(this.exerciseId, { assessedByTutor: true }, correctionRound);
                    break;
                case ExerciseType.FILE_UPLOAD:
                    submissionsObservable = this.fileUploadSubmissionService.getSubmissions(this.exerciseId, { assessedByTutor: true }, correctionRound);
                    break;
                case ExerciseType.PROGRAMMING:
                    submissionsObservable = this.programmingSubmissionService.getSubmissions(this.exerciseId, { assessedByTutor: true }, correctionRound);
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

                this.assessedSubmissionsByRound.set(correctionRound, sub);
                this.sortSubmissionRows(correctionRound);
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
     * Get all submissions that don't have an assessment for all correction rounds
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
                submissionObservable = this.textSubmissionService.getSubmissionWithoutAssessment(this.exerciseId, 'head', correctionRound);
                break;
            case ExerciseType.MODELING:
                submissionObservable = this.modelingSubmissionService.getSubmissionWithoutAssessment(this.exerciseId, undefined, correctionRound);
                break;
            case ExerciseType.FILE_UPLOAD:
                submissionObservable = this.fileUploadSubmissionService.getSubmissionWithoutAssessment(this.exerciseId, undefined, correctionRound);
                break;
            case ExerciseType.PROGRAMMING:
                submissionObservable = this.programmingSubmissionService.getSubmissionWithoutAssessment(this.exerciseId, undefined, correctionRound);
                break;
        }

        submissionObservable.subscribe({
            next: (submission: Submission) => {
                if (!submission) {
                    // there are no unassessed submission, nothing we have to worry about
                    // Delete this correction round, as we are done with all
                    if (this.unassessedSubmissionByRound) {
                        this.unassessedSubmissionByRound.delete(correctionRound);
                    }
                }

                setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
                this.unassessedSubmissionByRound!.set(correctionRound, submission);

                this.submissionLockLimitReached = false;
            },
            error: (error: HttpErrorResponse) => {
                if (error.error?.errorKey === 'lockedSubmissionsLimitReached') {
                    this.submissionLockLimitReached = true;
                } else {
                    this.onError(error.error?.detail || error.message);
                }
            },
        });
    }

    /**
     * Whether to show graded submissions and actions regarding them
     * @param correctionRound Round to check for unassessed submissions
     */
    showSubmissionsForRound(correctionRound: number): boolean {
        // TODO: booleans based on falsyness defy typescripts intention
        const unassessedSubmissionExist = !!this.unassessedSubmissionByRound?.get(correctionRound)?.id;
        const assessedSubmissionsExist = !!this.assessedSubmissionsByRound.get(correctionRound)?.length;

        return (unassessedSubmissionExist || assessedSubmissionsExist) && !this.exercise.allowComplaintsForAutomaticAssessments;
    }

    /**
     * Called after the tutor has read the instructions and creates a new tutor participation
     */
    readInstruction() {
        this.isLoading = true;
        this.tutorParticipationService
            .create(this.tutorParticipation, this.exerciseId)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: (res: HttpResponse<TutorParticipation>) => {
                    this.tutorParticipation = res.body!;
                    this.tutorParticipationStatus = this.tutorParticipation.status!;
                    this.alertService.success('artemisApp.exerciseAssessmentDashboard.participation.instructionsReviewed');
                },
                error: this.onError,
            });
    }

    /**
     * Returns whether the example submission for the given id has already been completed
     * @param exampleSubmissionId Id of the example submission which to check for completion
     */
    hasBeenCompletedByTutor(exampleSubmissionId: number) {
        return this.exampleSubmissionsCompletedByTutor.filter((exampleSubmission) => exampleSubmission.id === exampleSubmissionId).length > 0;
    }

    private onError(error: string) {
        this.alertService.error(error);
    }

    /**
     * Calculates the status of a submission by inspecting the result. Returns true if the submission is a draft, or false if it is done
     * @param submission which to check
     * @param correctionRound for which to get status
     */
    calculateSubmissionStatusIsDraft(submission: Submission, correctionRound = 0): boolean {
        const tmpResult = submission.results?.[correctionRound];
        return !(tmpResult && tmpResult!.completionDate && Result.isManualResult(tmpResult!));
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
     * Generates and returns the link to the assessment editor without query parameters
     * @param submission Either submission or 'new'.
     */
    getAssessmentLink(submission: Submission | 'new'): string[] {
        const submissionUrlParameter: number | 'new' = submission === 'new' ? 'new' : submission.id!;
        let participationId = undefined;
        if (submission !== 'new' && submission.participation !== undefined) {
            participationId = submission.participation!.id;
        }
        return getLinkToSubmissionAssessment(this.exercise.type!, this.courseId!, this.exerciseId, participationId, submissionUrlParameter, this.examId, this.exerciseGroupId);
    }

    /**
     * Generates and returns the query parameters required for opening the assessment editor
     * @param correctionRound
     */
    getAssessmentQueryParams(correctionRound = 0): object {
        if (this.isTestRun) {
            return {
                testRun: this.isTestRun,
                'correction-round': correctionRound,
            };
        } else {
            return { 'correction-round': correctionRound };
        }
    }

    /**
     * Generates and returns the query parameters required for opening a complaint
     * @param complaint
     */
    getComplaintQueryParams(complaint: Complaint) {
        const submission: Submission = complaint.result?.submission!;
        // numberOfAssessmentsOfCorrectionRounds size is the number of correction rounds
        if (complaint.complaintType === ComplaintType.MORE_FEEDBACK) {
            return this.getAssessmentQueryParams(this.numberOfAssessmentsOfCorrectionRounds.length - 1);
        }
        const result = this.getSubmissionToViewFromComplaintSubmission(submission);

        if (result !== undefined) {
            return this.getAssessmentQueryParams(result.results!.length - 1);
        }
    }

    /**
     * Returns either the corresponding submission to view or undefined.
     * This complaint has to be a true complaint or else issues can arise.
     * @param submission
     */
    getSubmissionToViewFromComplaintSubmission(submission: Submission): Submission | undefined {
        const submissionToView = this.submissionsWithComplaints.find((dto) => dto.submission.id === submission.id)?.submission;
        if (submissionToView) {
            if (submissionToView.results) {
                submissionToView.results = submissionToView.results.filter((result) => result.assessmentType !== AssessmentType.AUTOMATIC);
            } else {
                submissionToView.results = [];
            }
            return submissionToView;
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

    /**
     * Handles the click event of a user on a part of the chart.
     * Given the user has the right permissions, navigates to the submissions page of the exercise
     * @param event event that is delegated by ngx-charts. Used here to trigger the delegation only if the user clicks on the pie chart
     * not the legend
     */
    navigateToExerciseSubmissionOverview(event: any): void {
        if (!this.accountService.hasAnyAuthorityDirect([Authority.INSTRUCTOR])) {
            return;
        }
        // If the user selects a part in the pie, the corresponding event contains the name of the selected part as attribute
        // If the user selects the legend entry, the event consists only of the legend label as string
        const identifier = event.name ?? event;
        let index = 0;
        const route = ['course-management', this.courseId, this.exercise.type! + '-exercises', this.exerciseId, 'submissions'];
        if (this.isAutomaticAssessedProgrammingExercise) {
            // the filter option for complaints are an element of {3,4}.
            // We give an offset of 3 in advance to determine the correct filter option via the chart part names
            index = 3;
            route[4] = 'complaints';
        }
        this.assessments.forEach((data, i) => {
            if (data.name === identifier) {
                index += i;
            }
        });

        this.navigationUtilService.routeInNewTab(route, { queryParams: { filterOption: index } });
    }

    sortSubmissionRows(correctionRound: number) {
        this.sortService.sortByProperty(
            this.assessedSubmissionsByRound.get(correctionRound)!,
            this.sortPredicates[0].replace('correctionRound', correctionRound + ''),
            this.reverseOrders[0],
        );
    }

    sortComplaintRows() {
        // If the selected sort predicate is indifferent about two elements, the one submitted earlier should be displayed on top
        this.sortService.sortByProperty(this.submissionsWithComplaints, 'complaint.submittedTime', true);
        if (this.sortPredicates[1] === 'responseTime') {
            this.sortService.sortByFunction(this.submissionsWithComplaints, (element) => this.complaintService.getResponseTimeInSeconds(element.complaint), this.reverseOrders[1]);
        } else {
            this.sortService.sortByProperty(this.submissionsWithComplaints, this.sortPredicates[1], this.reverseOrders[1]);
        }
    }

    sortMoreFeedbackRows() {
        // If the selected sort predicate is indifferent about two elements, the one submitted earlier should be displayed on top
        this.sortService.sortByProperty(this.submissionsWithMoreFeedbackRequests, 'complaint.submittedTime', true);
        if (this.sortPredicates[2] === 'responseTime') {
            this.sortService.sortByFunction(
                this.submissionsWithMoreFeedbackRequests,
                (element) => this.complaintService.getResponseTimeInSeconds(element.complaint),
                this.reverseOrders[2],
            );
        } else {
            this.sortService.sortByProperty(this.submissionsWithMoreFeedbackRequests, this.sortPredicates[2], this.reverseOrders[2]);
        }
    }
}
