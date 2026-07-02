import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { User } from 'app/account/user/user.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { UMLModel, importDiagram } from '@tumaet/apollon';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import {
    Submission,
    SubmissionExerciseType,
    getLatestSubmissionResult,
    getSubmissionResultByCorrectionRound,
    setLatestSubmissionResult,
} from 'app/exercise/shared/entities/submission/submission.model';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { Observable, of } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { TranslateService } from '@ngx-translate/core';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { AccountService } from 'app/core/auth/account.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TutorParticipation, TutorParticipationDTO, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SubmissionService, SubmissionWithComplaintDTO } from 'app/exercise/submission/submission.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { SortService } from 'app/foundation/service/sort.service';
import { onError } from 'app/foundation/util/global.utils';
import { parseJson } from 'app/foundation/util/json.util';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ChartModule } from 'primeng/chart';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { doughnutChartOptions } from 'app/shared-ui/chart/chart-options';
import dayjs from 'dayjs/esm';
import { faCheckCircle, faExclamationTriangle, faFolderOpen, faListAlt, faQuestionCircle, faSort, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { isManualResult } from 'app/exercise/result/result.utils';
import { TutorParticipationGraphComponent } from 'app/exercise/dashboards/tutor-participation-graph/tutor-participation-graph.component';
import { SecondCorrectionEnableButtonComponent } from './second-correction-button/second-correction-enable-button.component';
import { SidePanelComponent } from 'app/shared-ui/side-panel/side-panel.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { LanguageTableCellComponent } from './language-table-cell/language-table-cell.component';
import { NgStyle } from '@angular/common';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { TutorLeaderboardComponent } from 'app/exercise/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { AssessmentDashboardInformationEntry } from 'app/assessment/shared/assessment-dashboard/assessment-dashboard-information.component';
import { HeaderExercisePageWithDetailsComponent } from 'app/exercise/exercise-headers/with-details/header-exercise-page-with-details.component';
import { InfoPanelComponent } from 'app/assessment/shared/info-panel/info-panel.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';

export interface ExampleSubmissionQueryParams {
    readOnly?: boolean;
    toComplete?: boolean;
}

@Component({
    selector: 'jhi-exercise-assessment-dashboard',
    templateUrl: './exercise-assessment-dashboard.component.html',
    styleUrls: ['./exercise-assessment-dashboard.component.scss'],
    providers: [CourseManagementService],
    imports: [
        HeaderExercisePageWithDetailsComponent,
        TutorParticipationGraphComponent,
        SecondCorrectionEnableButtonComponent,
        ChartModule,
        SidePanelComponent,
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        InfoPanelComponent,
        ProgrammingExerciseInstructionComponent,
        ModelingEditorComponent,
        CodeButtonComponent,
        StructuredGradingInstructionsAssessmentLayoutComponent,
        NgbTooltip,
        SortDirective,
        SortByDirective,
        LanguageTableCellComponent,
        NgStyle,
        ResultComponent,
        AssessmentWarningComponent,
        CollapsableAssessmentInstructionsComponent,
        TutorLeaderboardComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
    ],
})
export class ExerciseAssessmentDashboardComponent implements OnInit {
    complaintService = inject(ComplaintService);
    private exerciseService = inject(ExerciseService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private accountService = inject(AccountService);
    private route = inject(ActivatedRoute);
    private tutorParticipationService = inject(TutorParticipationService);
    private submissionService = inject(SubmissionService);
    private textSubmissionService = inject(TextSubmissionService);
    private modelingSubmissionService = inject(ModelingSubmissionService);
    private fileUploadSubmissionService = inject(FileUploadSubmissionService);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private router = inject(Router);
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private sortService = inject(SortService);

    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

    // Async-loaded, template-bound state — signals so writes schedule change detection under zoneless (no markForCheck).
    readonly exercise = signal<Exercise>(undefined!);
    readonly modelingExercise = signal<ModelingExercise>(undefined!);
    readonly programmingExercise = signal<ProgrammingExercise>(undefined!);
    readonly courseId = signal<number>(undefined!);
    readonly exam = signal<Exam | undefined>(undefined);
    examId: number;
    exerciseGroupId: number;
    readonly isExamMode = signal(false);
    readonly isTestRun = signal<boolean>(false);
    readonly isLoading = signal(false);

    readonly statsForDashboard = signal(new StatsForDashboard());

    readonly exerciseId = signal<number>(undefined!);
    readonly numberOfTutorAssessments = signal(0);
    readonly numberOfSubmissions = signal(new DueDateStat());
    readonly totalNumberOfAssessments = signal(0);
    readonly numberOfAutomaticAssistedAssessments = signal(new DueDateStat());
    readonly numberOfAssessmentsOfCorrectionRounds = signal<DueDateStat[]>([new DueDateStat()]);
    readonly numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = signal<DueDateStat[]>([new DueDateStat()]);
    readonly complaintsEnabled = signal(false);
    readonly feedbackRequestEnabled = signal(false);
    readonly tutorAssessmentPercentage = signal(0);
    readonly tutorParticipationStatus = signal<TutorParticipationStatus>(undefined!);
    // Indexed by correction round (0, 1) — readonly so any in-place mutation is a compile error and updates go through .update() with a new array.
    readonly assessedSubmissionsByRound = signal<readonly (readonly Submission[])[]>([]);
    readonly unassessedSubmissionByRound = signal<readonly (Submission | undefined)[]>([]);
    readonly exampleSubmissionsToReview = signal<ExampleSubmission[]>([]);
    readonly exampleSubmissionsToAssess = signal<ExampleSubmission[]>([]);
    readonly exampleSubmissionsCompletedByTutor = signal<ExampleSubmission[]>([]);
    readonly tutorParticipation = signal<TutorParticipation>(undefined!);
    readonly nextExampleSubmissionId = signal<number>(undefined!);
    readonly exampleSolutionModel = signal<UMLModel>(undefined!);
    readonly complaints = signal<Complaint[]>([]);
    readonly submissionsWithMoreFeedbackRequests = signal<SubmissionWithComplaintDTO[]>([]);
    readonly submissionsWithComplaints = signal<SubmissionWithComplaintDTO[]>([]);
    readonly submissionLockLimitReached = signal(false);
    readonly openingAssessmentEditorForNewSubmission = signal(false);
    readonly secondCorrectionEnabled = signal(false);
    readonly numberOfCorrectionRoundsEnabled = computed(() => (this.secondCorrectionEnabled() ? 2 : 1));

    readonly formattedGradingInstructions = signal<SafeHtml | undefined>(undefined);
    readonly formattedProblemStatement = signal<SafeHtml | undefined>(undefined);
    readonly formattedSampleSolution = signal<SafeHtml | undefined>(undefined);
    getSubmissionResultByCorrectionRound = getSubmissionResultByCorrectionRound;

    // helper variables to display information message about why no new assessments are possible anymore.
    // Computed synchronously in calculateAssessmentProgressInformation alongside statsForDashboard.set(), so they render on that signal's CD tick.
    lockedSubmissionsByOtherTutor: number[] = [];
    notYetAssessed: number[] = [];
    readonly firstRoundAssessments = signal<number>(undefined!);

    // attributes for sorting the tables
    sortPredicates = ['submissionDate', 'complaint.accepted', 'complaint.accepted'];
    reverseOrders = [true, false, false];

    readonly ExerciseType = ExerciseType;
    protected readonly RepositoryType = RepositoryType;

    // Mutated in place only within the getForTutors subscribe (alongside exercise.set()), so it renders on that signal's CD tick.
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

    readonly tutor = signal<User | undefined>(undefined);
    readonly togglingSecondCorrectionButton = signal(false);

    readonly complaintsDashboardInfo = signal(new AssessmentDashboardInformationEntry(0, 0));
    readonly moreFeedbackRequestsDashboardInfo = signal(new AssessmentDashboardInformationEntry(0, 0));
    readonly ratingsDashboardInfo = signal(new AssessmentDashboardInformationEntry(0, 0));

    // graph (rebuilt in setupGraph, which also runs on the async onLangChange — signals so the chart re-renders under zoneless)
    private readonly chartEntries = signal<ChartSeriesEntry[]>([]);
    // raw colors (CSS variable references), index-aligned with chartEntries; resolved theme-reactively below
    private readonly rawChartColors = signal<string[]>([]);
    private readonly resolvedChartColors = inject(ChartColorService).resolvedColors(() => this.rawChartColors());

    readonly chartData = computed(() => singleSeriesChartData(this.chartEntries(), this.resolvedChartColors()));
    readonly chartOptions = computed(() =>
        doughnutChartOptions({
            arcWidth: 1,
            legend: { position: 'bottom' },
            tooltip: { label: (item) => `${((item.parsed * 100) / this.numberOfSubmissions().total).toFixed(2)}%` },
        }),
    );
    readonly isAutomaticAssessedProgrammingExercise = signal(false);

    // links (set in setupLinks alongside exercise.set() in the getForTutors subscribe)
    readonly complaintsLink = signal<any[]>(undefined!);
    readonly moreFeedbackRequestsLink = signal<any[]>(undefined!);

    // Icons
    faSpinner = faSpinner;
    faQuestionCircle = faQuestionCircle;
    faCheckCircle = faCheckCircle;
    faFolderOpen = faFolderOpen;
    faSort = faSort;
    faExclamationTriangle = faExclamationTriangle;
    faListAlt = faListAlt;

    /**
     * Extracts the course and exercise ids from the route params and fetches the exercise from the server
     */
    ngOnInit(): void {
        this.exerciseId.set(Number(this.route.snapshot.paramMap.get('exerciseId')));
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.isTestRun.set(this.router.url.indexOf('test-assessment-dashboard') >= 0);
        this.unassessedSubmissionByRound.set([]);

        if (this.route.snapshot.paramMap.has('examId')) {
            this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        }

        this.tutor.set(this.accountService.userIdentity());

        this.loadAll();

        this.translateService.onLangChange.subscribe(() => {
            this.setupGraph();
        });
    }

    setupGraph() {
        // If the programming exercise is assessed automatically but complaints are enabled, the term "unassessed submissions" might be misleading.
        // In this case, we only show open and resolved complaints
        if (
            this.programmingExercise() &&
            this.programmingExercise().assessmentType === AssessmentType.AUTOMATIC &&
            this.programmingExercise().allowComplaintsForAutomaticAssessments
        ) {
            const numberOfComplaintsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfOpenComplaints');
            const numberOfResolvedComplaintsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfResolvedComplaints');
            this.isAutomaticAssessedProgrammingExercise.set(true);
            this.rawChartColors.set([GraphColors.YELLOW, GraphColors.GREEN]);
            this.chartEntries.set([
                {
                    name: numberOfComplaintsLabel,
                    value: this.statsForDashboard().numberOfOpenComplaints,
                },
                {
                    name: numberOfResolvedComplaintsLabel,
                    value: this.statsForDashboard().numberOfComplaints - this.statsForDashboard().numberOfOpenComplaints,
                },
            ]);
        } else {
            const numberOfUnassessedSubmissionLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfUnassessedSubmissions');
            const numberOfAutomaticAssistedSubmissionsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfAutomaticAssistedSubmissions');
            const numberOfManualAssessedSubmissionsLabel = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.numberOfManualAssessedSubmissions');
            this.rawChartColors.set([GraphColors.RED, GraphColors.BLUE, GraphColors.YELLOW]);
            this.chartEntries.set([
                {
                    name: numberOfUnassessedSubmissionLabel,
                    value: this.numberOfSubmissions().total - this.totalNumberOfAssessments(),
                },
                {
                    name: numberOfManualAssessedSubmissionsLabel,
                    value: this.totalNumberOfAssessments() - this.numberOfAutomaticAssistedAssessments().total,
                },
                {
                    name: numberOfAutomaticAssistedSubmissionsLabel,
                    value: this.numberOfAutomaticAssistedAssessments().total,
                },
            ]);
        }
    }

    setupLinks() {
        this.complaintsLink.set(['/course-management', this.courseId(), this.exercise().type! + '-exercises', this.exercise().id!, 'complaints']);
        this.moreFeedbackRequestsLink.set(['/course-management', this.courseId(), this.exercise().type! + '-exercises', this.exercise().id!, 'more-feedback-requests']);
    }

    /**
     * Loads all information from the server regarding this exercise that is needed for the tutor exercise dashboard
     */
    loadAll() {
        this.exerciseService.getForTutors(this.exerciseId()).subscribe({
            next: (res: HttpResponse<Exercise>) => {
                const exercise = res.body!;
                this.exercise.set(exercise);
                this.secondCorrectionEnabled.set(exercise.secondCorrectionEnabled);
                this.formattedGradingInstructions.set(this.artemisMarkdown.safeHtmlForMarkdown(exercise.gradingInstructions));
                this.formattedProblemStatement.set(this.artemisMarkdown.safeHtmlForMarkdown(exercise.problemStatement));

                switch (exercise.type) {
                    case ExerciseType.TEXT:
                        const textExercise = exercise as TextExercise;
                        this.formattedSampleSolution.set(this.artemisMarkdown.safeHtmlForMarkdown(textExercise.exampleSolution));
                        break;
                    case ExerciseType.MODELING:
                        const modelingExercise = exercise as ModelingExercise;
                        this.modelingExercise.set(modelingExercise);
                        if (modelingExercise.exampleSolutionModel) {
                            this.formattedSampleSolution.set(this.artemisMarkdown.safeHtmlForMarkdown(modelingExercise.exampleSolutionExplanation));
                            this.exampleSolutionModel.set(importDiagram(parseJson(modelingExercise.exampleSolutionModel)));
                        }
                        break;
                    case ExerciseType.FILE_UPLOAD:
                        const fileUploadExercise = exercise as FileUploadExercise;
                        this.formattedSampleSolution.set(this.artemisMarkdown.safeHtmlForMarkdown(fileUploadExercise.exampleSolution));
                        break;
                    case ExerciseType.PROGRAMMING:
                        this.programmingExercise.set(exercise as ProgrammingExercise);
                        break;
                }

                const tutorParticipation = exercise.tutorParticipations![0];
                this.tutorParticipation.set(tutorParticipation);
                this.tutorParticipationStatus.set(tutorParticipation.status!);
                if (exercise.exampleSubmissions && exercise.exampleSubmissions.length > 0) {
                    this.exampleSubmissionsToReview.set(exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => !exampleSubmission.usedForTutorial));
                    this.exampleSubmissionsToAssess.set(exercise.exampleSubmissions.filter((exampleSubmission: ExampleSubmission) => exampleSubmission.usedForTutorial));
                }
                this.exampleSubmissionsCompletedByTutor.set(tutorParticipation.trainedExampleSubmissions || []);

                this.stats.toReview.total = this.exampleSubmissionsToReview().length;
                this.stats.toReview.done = this.exampleSubmissionsCompletedByTutor().filter((e) => !e.usedForTutorial).length;
                this.stats.toAssess.total = this.exampleSubmissionsToAssess().length;
                this.stats.toAssess.done = this.exampleSubmissionsCompletedByTutor().filter((e) => e.usedForTutorial).length;

                if (this.stats.toReview.done < this.stats.toReview.total) {
                    this.nextExampleSubmissionId.set(this.exampleSubmissionsToReview()[this.stats.toReview.done].id!);
                } else if (this.stats.toAssess.done < this.stats.toAssess.total) {
                    this.nextExampleSubmissionId.set(this.exampleSubmissionsToAssess()[this.stats.toAssess.done].id!);
                }

                // exercise belongs to an exam
                if (exercise.exerciseGroup) {
                    this.isExamMode.set(true);
                    this.exam.set(exercise.exerciseGroup.exam);
                    this.exerciseGroupId = exercise.exerciseGroup.id!;
                    this.secondCorrectionEnabled.set(exercise.secondCorrectionEnabled);
                }
                this.getAllTutorAssessedSubmissionsForAllCorrectionRounds();

                // The assessment for team exercises is not started from the tutor exercise dashboard but from the team pages
                const isAfterDueDate = !exercise.dueDate || exercise.dueDate.isBefore(dayjs());
                if (((exercise.course?.athenaFormativeFeedbackEnabled ?? false) || isAfterDueDate) && !exercise.teamMode && !this.isTestRun()) {
                    this.getSubmissionWithoutAssessmentForAllCorrectionRounds();
                }

                this.setupLinks();
            },
            error: (response: string) => this.onError(response),
        });

        if (!this.isTestRun()) {
            this.submissionService.getSubmissionsWithComplaintsForTutor(this.exerciseId()).subscribe({
                next: (res: HttpResponse<SubmissionWithComplaintDTO[]>) => {
                    this.submissionsWithComplaints.set(res.body || []);
                    this.sortComplaintRows();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });

            this.submissionService.getSubmissionsWithMoreFeedbackRequestsForTutor(this.exerciseId()).subscribe({
                next: (res: HttpResponse<SubmissionWithComplaintDTO[]>) => {
                    this.submissionsWithMoreFeedbackRequests.set(res.body || []);
                    this.sortMoreFeedbackRows();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });

            this.exerciseService.getStatsForTutors(this.exerciseId()).subscribe({
                next: (res: HttpResponse<StatsForDashboard>) => {
                    this.statsForDashboard.set(StatsForDashboard.from(res.body!));
                    this.numberOfSubmissions.set(this.statsForDashboard().numberOfSubmissions);
                    this.totalNumberOfAssessments.set(this.statsForDashboard().totalNumberOfAssessments);
                    this.numberOfAssessmentsOfCorrectionRounds.set(this.statsForDashboard().numberOfAssessmentsOfCorrectionRounds);
                    this.numberOfAutomaticAssistedAssessments.set(this.statsForDashboard().numberOfAutomaticAssistedAssessments);
                    this.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound.set(this.statsForDashboard().numberOfLockedAssessmentByOtherTutorsOfCorrectionRound);

                    const tutorLeaderboardEntry = this.statsForDashboard().tutorLeaderboardEntries?.find((entry) => entry.userId === this.tutor()!.id);
                    this.sortService.sortByProperty(this.statsForDashboard().tutorLeaderboardEntries, 'points', false);

                    // Prepare the table data for the side table
                    if (tutorLeaderboardEntry) {
                        this.numberOfTutorAssessments.set(tutorLeaderboardEntry.numberOfAssessments);
                        this.complaintsDashboardInfo.set(
                            new AssessmentDashboardInformationEntry(
                                this.statsForDashboard().numberOfComplaints,
                                tutorLeaderboardEntry.numberOfTutorComplaints,
                                this.statsForDashboard().numberOfComplaints - this.statsForDashboard().numberOfOpenComplaints,
                            ),
                        );
                        this.moreFeedbackRequestsDashboardInfo.set(
                            new AssessmentDashboardInformationEntry(
                                this.statsForDashboard().numberOfMoreFeedbackRequests,
                                tutorLeaderboardEntry.numberOfTutorMoreFeedbackRequests,
                                this.statsForDashboard().numberOfMoreFeedbackRequests - this.statsForDashboard().numberOfOpenMoreFeedbackRequests,
                            ),
                        );
                        this.ratingsDashboardInfo.set(
                            new AssessmentDashboardInformationEntry(this.statsForDashboard().numberOfRatings, tutorLeaderboardEntry.numberOfTutorRatings),
                        );
                    } else {
                        this.numberOfTutorAssessments.set(0);
                        this.complaintsDashboardInfo.set(
                            new AssessmentDashboardInformationEntry(
                                this.statsForDashboard().numberOfComplaints,
                                0,
                                this.statsForDashboard().numberOfComplaints - this.statsForDashboard().numberOfOpenComplaints,
                            ),
                        );
                        this.moreFeedbackRequestsDashboardInfo.set(
                            new AssessmentDashboardInformationEntry(
                                this.statsForDashboard().numberOfMoreFeedbackRequests,
                                0,
                                this.statsForDashboard().numberOfMoreFeedbackRequests - this.statsForDashboard().numberOfOpenMoreFeedbackRequests,
                            ),
                        );
                        this.ratingsDashboardInfo.set(new AssessmentDashboardInformationEntry(this.statsForDashboard().numberOfRatings, 0));
                    }

                    if (this.numberOfSubmissions().total > 0) {
                        this.tutorAssessmentPercentage.set(Math.floor((this.numberOfTutorAssessments() / this.numberOfSubmissions().total) * 100));
                    } else {
                        this.tutorAssessmentPercentage.set(100);
                    }
                    this.complaintsEnabled.set(this.statsForDashboard().complaintsEnabled);
                    this.feedbackRequestEnabled.set(this.statsForDashboard().feedbackRequestEnabled);
                    this.calculateAssessmentProgressInformation();

                    this.setupGraph();
                },
                error: (response: string) => this.onError(response),
            });
        } else {
            this.complaintService.getComplaintsForTestRun(this.exerciseId()).subscribe({
                next: (res: HttpResponse<ComplaintDTO[]>) => {
                    this.complaints.set(res.body?.map((complaintDTO) => this.complaintService.convertComplaintFromServerInList(complaintDTO)) ?? []);
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }

    get yourStatusTitle(): string {
        switch (this.tutorParticipationStatus()) {
            case TutorParticipationStatus.TRAINED:
                // If we are in 'TRAINED' state, but never really "trained" on example submissions, display the
                // 'REVIEWED_INSTRUCTIONS' state text instead.
                const exampleSubmissions = this.exercise().exampleSubmissions;
                if (!exampleSubmissions || exampleSubmissions.length === 0) {
                    return TutorParticipationStatus.REVIEWED_INSTRUCTIONS.toString();
                }

                return TutorParticipationStatus.TRAINED.toString();
            default:
                return this.tutorParticipationStatus().toString();
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
     */
    private getAllTutorAssessedSubmissionsForAllCorrectionRounds(): void {
        if (this.isExamMode()) {
            for (let i = 0; i < this.exam()!.numberOfCorrectionRoundsInExam!; i++) {
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
        if (this.isTestRun()) {
            submissionsObservable = this.submissionService.getTestRunSubmissionsForExercise(this.exerciseId());
        } else {
            // TODO: This could be one generic endpoint.
            switch (this.exercise().type) {
                case ExerciseType.TEXT:
                    submissionsObservable = this.textSubmissionService.getSubmissions(this.exerciseId(), { assessedByTutor: true }, correctionRound);
                    break;
                case ExerciseType.MODELING:
                    submissionsObservable = this.modelingSubmissionService.getSubmissions(this.exerciseId(), { assessedByTutor: true }, correctionRound);
                    break;
                case ExerciseType.FILE_UPLOAD:
                    submissionsObservable = this.fileUploadSubmissionService.getSubmissions(this.exerciseId(), { assessedByTutor: true }, correctionRound);
                    break;
                case ExerciseType.PROGRAMMING:
                    submissionsObservable = this.programmingSubmissionService.getSubmissions(this.exerciseId(), { assessedByTutor: true }, correctionRound);
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
                        setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
                        return submission;
                    });

                this.assessedSubmissionsByRound.update((rounds) => {
                    const next = [...rounds];
                    next[correctionRound] = sub;
                    return next;
                });
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
                setLatestSubmissionResult(submission, latestResult);
            }
            return submission;
        });
    };

    /**
     * Get all submissions that don't have an assessment for all correction rounds
     * If not in examMode correction rounds defaults to 0.
     */
    private getSubmissionWithoutAssessmentForAllCorrectionRounds(): void {
        if (this.isExamMode()) {
            for (let i = 0; i < this.exam()!.numberOfCorrectionRoundsInExam!; i++) {
                if (i <= this.numberOfCorrectionRoundsEnabled()) {
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
        let submissionObservable: Observable<Submission | undefined> = of();
        switch (this.exercise().type) {
            case ExerciseType.TEXT:
                submissionObservable = this.textSubmissionService.getSubmissionWithoutAssessment(this.exerciseId(), 'head', correctionRound);
                break;
            case ExerciseType.MODELING:
                submissionObservable = this.modelingSubmissionService.getSubmissionWithoutAssessment(this.exerciseId(), undefined, correctionRound);
                break;
            case ExerciseType.FILE_UPLOAD:
                submissionObservable = this.fileUploadSubmissionService.getSubmissionWithoutAssessment(this.exerciseId(), undefined, correctionRound);
                break;
            case ExerciseType.PROGRAMMING:
                submissionObservable = this.programmingSubmissionService.getSubmissionWithoutAssessment(this.exerciseId(), undefined, correctionRound);
                break;
        }

        submissionObservable.subscribe({
            next: (submission?: Submission) => {
                if (!submission) {
                    // there are no unassessed submissions
                    // Delete this correction round, as we are done with all
                    this.unassessedSubmissionByRound.update((rounds) => {
                        const next = [...rounds];
                        next[correctionRound] = undefined;
                        return next;
                    });
                    return;
                }

                setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
                this.unassessedSubmissionByRound.update((rounds) => {
                    const next = [...rounds];
                    next[correctionRound] = submission;
                    return next;
                });

                this.submissionLockLimitReached.set(false);
            },
            error: (error: HttpErrorResponse) => {
                if (error.error?.errorKey === 'lockedSubmissionsLimitReached') {
                    this.submissionLockLimitReached.set(true);
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
        const unassessedSubmissionExist = !!this.unassessedSubmissionByRound()[correctionRound]?.id;
        const assessedSubmissionsExist = !!this.assessedSubmissionsByRound()[correctionRound]?.length;

        return (unassessedSubmissionExist || assessedSubmissionsExist) && !this.exercise().allowComplaintsForAutomaticAssessments;
    }

    /**
     * Called after the tutor has read the instructions and creates a new tutor participation
     */
    readInstruction() {
        this.isLoading.set(true);
        this.tutorParticipationService
            .create(this.exerciseId())
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe({
                next: (res: HttpResponse<TutorParticipationDTO>) => {
                    const dto = res.body!;
                    this.tutorParticipation.set(dto);
                    this.tutorParticipationStatus.set(dto.status!);
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
        return this.exampleSubmissionsCompletedByTutor().filter((exampleSubmission) => exampleSubmission.id === exampleSubmissionId).length > 0;
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
        return !(tmpResult?.completionDate && isManualResult(tmpResult));
    }

    /**
     * Uses the router to navigate to a given example submission
     * @param submissionId Id of submission where to navigate to
     * @param readOnly Flag whether the view should be opened in read-only mode
     * @param toComplete Flag whether the view should be opened in to-complete mode
     */
    openExampleSubmission(submissionId: number, readOnly?: boolean, toComplete?: boolean) {
        if (!this.exercise()?.type || !submissionId) {
            return;
        }
        const route = `/course-management/${this.courseId()}/${this.exercise().type}-exercises/${this.exercise().id}/example-submissions/${submissionId}`;
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
        return this.complaintService.isComplaintLockedForLoggedInUser(complaint, this.exercise());
    }

    /**
     * Generates and returns the link to the assessment editor without query parameters
     * @param submission Either submission or 'new'.
     */
    getAssessmentLink(submission: Submission | 'new'): string[] {
        const submissionUrlParameter: number | 'new' = submission === 'new' ? 'new' : submission.id!;
        let participationId = undefined;
        if (submission !== 'new' && submission.participation) {
            participationId = submission.participation.id;
        }
        return getLinkToSubmissionAssessment(this.exercise().type!, this.courseId(), this.exerciseId(), participationId, submissionUrlParameter, this.examId, this.exerciseGroupId);
    }

    /**
     * Generates and returns the query parameters required for opening the assessment editor
     * @param correctionRound
     */
    getAssessmentQueryParams(correctionRound = 0): object {
        if (this.isTestRun()) {
            return {
                testRun: this.isTestRun(),
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
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        const submission: Submission = complaint.result?.submission!;
        // numberOfAssessmentsOfCorrectionRounds size is the number of correction rounds
        if (complaint.complaintType === ComplaintType.MORE_FEEDBACK) {
            return this.getAssessmentQueryParams(this.numberOfAssessmentsOfCorrectionRounds().length - 1);
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
        const submissionToView = this.submissionsWithComplaints().find((dto) => dto.submission.id === submission.id)?.submission;
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
        this.togglingSecondCorrectionButton.set(true);
        this.exerciseService.toggleSecondCorrection(this.exerciseId()).subscribe((res: boolean) => {
            this.secondCorrectionEnabled.set(res as boolean);
            this.getSubmissionWithoutAssessmentForAllCorrectionRounds();
            this.togglingSecondCorrectionButton.set(false);
        });
    }

    /**
     * To correctly display why a tutor cannot assess any further submissions we need to calculate those values.
     */
    calculateAssessmentProgressInformation() {
        const exam = this.exam();
        if (exam) {
            for (let i = 0; i < (exam.numberOfCorrectionRoundsInExam ?? 0); i++) {
                this.lockedSubmissionsByOtherTutor[i] = this.numberOfLockedAssessmentByOtherTutorsOfCorrectionRound()[i]?.inTime;

                // number of submissions in the particular round that still need assessment and are not locked
                this.notYetAssessed[i] = this.numberOfSubmissions().inTime - this.numberOfAssessmentsOfCorrectionRounds()[i].inTime - this.lockedSubmissionsByOtherTutor[i];

                // The number of assessments which are still open but cannot be assessed as they were already assessed in the first round.
                // Since if this will be displayed the number of assessments the tutor can create is 0 we can simply get this number by subtracting the
                // lock-count from the remaining unassessed submissions of the current correction round.
                this.firstRoundAssessments.set(this.notYetAssessed[i] - this.lockedSubmissionsByOtherTutor[i]);
            }
        }
    }

    sortSubmissionRows(correctionRound: number) {
        this.assessedSubmissionsByRound.update((rounds) => {
            const next = [...rounds];
            const sorted = [...(next[correctionRound] ?? [])];
            this.sortService.sortByProperty(sorted, this.sortPredicates[0].replace('correctionRound', correctionRound + ''), this.reverseOrders[0]);
            next[correctionRound] = sorted;
            return next;
        });
    }

    sortComplaintRows() {
        // If the selected sort predicate is indifferent about two elements, the one submitted earlier should be displayed on top
        const sorted = [...this.submissionsWithComplaints()];
        this.sortService.sortByProperty(sorted, 'complaint.submittedTime', true);
        if (this.sortPredicates[1] === 'responseTime') {
            this.sortService.sortByFunction(sorted, (element) => this.complaintService.getResponseTimeInSeconds(element.complaint), this.reverseOrders[1]);
        } else {
            this.sortService.sortByProperty(sorted, this.sortPredicates[1], this.reverseOrders[1]);
        }
        this.submissionsWithComplaints.set(sorted);
    }

    sortMoreFeedbackRows() {
        // If the selected sort predicate is indifferent about two elements, the one submitted earlier should be displayed on top
        const sorted = [...this.submissionsWithMoreFeedbackRequests()];
        this.sortService.sortByProperty(sorted, 'complaint.submittedTime', true);
        if (this.sortPredicates[2] === 'responseTime') {
            this.sortService.sortByFunction(sorted, (element) => this.complaintService.getResponseTimeInSeconds(element.complaint), this.reverseOrders[2]);
        } else {
            this.sortService.sortByProperty(sorted, this.sortPredicates[2], this.reverseOrders[2]);
        }
        this.submissionsWithMoreFeedbackRequests.set(sorted);
    }

    /**
     * Generates a link to the respective exercise details page
     */
    getExerciseDetailsLink() {
        return ['/course-management', this.courseId(), this.exercise().type! + '-exercises', this.exercise().id!];
    }
}
