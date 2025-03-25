import { Component, OnDestroy, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { ProgrammingExerciseBuildConfig } from 'app/entities/programming/programming-exercise-build.config';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistics.component';
import { Subject, Subscription, of } from 'rxjs';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming/programming-exercise-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercise/exercise.service';
import { ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import {
    faBook,
    faChartBar,
    faCheckDouble,
    faExclamationTriangle,
    faEye,
    faFileSignature,
    faListAlt,
    faPencilAlt,
    faRobot,
    faTable,
    faTrash,
    faUndo,
    faUserCheck,
    faUsers,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared/components/button.component';
import { ProgrammingLanguageFeatureService } from 'app/programming/service/programming-language-feature/programming-language-feature.service';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ConsistencyCheckService } from 'app/shared/consistency-check/consistency-check.service';
import { hasEditableBuildPlan } from 'app/shared/layouts/profiles/profile-info.model';
import { PROFILE_IRIS, PROFILE_LOCALCI, PROFILE_LOCALVC } from 'app/app.constants';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';
import { Detail } from 'app/shared/detail-overview-list/detail.model';
import { Competency } from 'app/entities/competency.model';
import { AeolusService } from 'app/programming/service/aeolus.service';
import { catchError, mergeMap, tap } from 'rxjs/operators';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/programming-exercise-git-diff-report.model';
import { BuildLogStatisticsDTO } from 'app/entities/programming/build-log-statistics-dto';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ProgrammingExerciseInstructorExerciseDownloadComponent } from 'app/programming/shared/actions/programming-exercise-instructor-exercise-download.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ProgrammingExerciseResetButtonDirective } from 'app/programming/manage/reset/programming-exercise-reset-button.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RepositoryType } from '../shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-programming-exercise-detail',
    templateUrl: './programming-exercise-detail.component.html',
    styleUrls: ['./programming-exercise-detail.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        RouterLink,
        FaIconComponent,
        FeatureToggleLinkDirective,
        NgbTooltip,
        ProgrammingExerciseInstructorExerciseDownloadComponent,
        FeatureToggleDirective,
        ProgrammingExerciseResetButtonDirective,
        DeleteButtonDirective,
        ExerciseDetailStatisticsComponent,
        DetailOverviewListComponent,
        ArtemisTranslatePipe,
    ],
})
export class ProgrammingExerciseDetailComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private accountService = inject(AccountService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    exerciseService = inject(ExerciseService);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private alertService = inject(AlertService);
    private programmingExerciseSubmissionPolicyService = inject(SubmissionPolicyService);
    private eventManager = inject(EventManager);
    modalService = inject(NgbModal);
    private translateService = inject(TranslateService);
    private profileService = inject(ProfileService);
    private statisticsService = inject(StatisticsService);
    private router = inject(Router);
    private programmingLanguageFeatureService = inject(ProgrammingLanguageFeatureService);
    private consistencyCheckService = inject(ConsistencyCheckService);
    private irisSettingsService = inject(IrisSettingsService);
    private aeolusService = inject(AeolusService);

    protected readonly dayjs = dayjs;
    protected readonly ActionType = ActionType;
    protected readonly ProgrammingExerciseParticipationType = ProgrammingExerciseParticipationType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    protected readonly ButtonSize = ButtonSize;
    protected readonly AssessmentType = AssessmentType;
    protected readonly RepositoryType = RepositoryType;
    protected readonly documentationType: DocumentationType = 'Programming';

    protected readonly faUndo = faUndo;
    protected readonly faTrash = faTrash;
    protected readonly faBook = faBook;
    protected readonly faWrench = faWrench;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faTable = faTable;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faFileSignature = faFileSignature;
    protected readonly faListAlt = faListAlt;
    protected readonly faChartBar = faChartBar;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faUsers = faUsers;
    protected readonly faEye = faEye;
    protected readonly faUserCheck = faUserCheck;
    protected readonly faRobot = faRobot;

    programmingExercise: ProgrammingExercise;
    programmingExerciseBuildConfig?: ProgrammingExerciseBuildConfig;
    competencies: Competency[];
    isExamExercise: boolean;
    supportsAuxiliaryRepositories: boolean;
    baseResource: string;
    shortBaseResource: string;
    teamBaseResource: string;
    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;
    courseId: number;
    doughnutStats: ExerciseManagementStatisticsDto;
    formattedGradingInstructions: SafeHtml;
    localVCEnabled = true;
    localCIEnabled = true;
    irisEnabled = false;
    irisChatEnabled = false;

    isAdmin = false;
    addedLineCount: number;
    removedLineCount: number;
    isLoadingDiffReport: boolean;
    isBuildPlanEditable = false;

    plagiarismCheckSupported = false; // default value

    private activatedRouteSubscription: Subscription;
    private templateAndSolutionParticipationSubscription: Subscription;
    private irisSettingsSubscription: Subscription;
    private exerciseStatisticsSubscription: Subscription;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    exerciseDetailSections: DetailOverviewSection[];

    ngOnInit() {
        this.checkBuildPlanEditable();

        this.activatedRouteSubscription = this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            this.programmingExerciseBuildConfig = programmingExercise.buildConfig;
            this.competencies = programmingExercise.competencies;
            const exerciseId = this.programmingExercise.id!;
            this.isExamExercise = !!this.programmingExercise.exerciseGroup;
            this.courseId = this.isExamExercise ? this.programmingExercise.exerciseGroup!.exam!.course!.id! : this.programmingExercise.course!.id!;
            this.isAdmin = this.accountService.isAdmin();
            this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.programmingExercise.gradingInstructions);

            if (!this.isExamExercise) {
                this.baseResource = `/course-management/${this.courseId}/programming-exercises/${exerciseId}/`;
                this.shortBaseResource = `/course-management/${this.courseId}/`;
                this.teamBaseResource = `/course-management/${this.courseId}/exercises/${exerciseId}/`;
            } else {
                this.baseResource =
                    `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}` +
                    `/exercise-groups/${this.programmingExercise.exerciseGroup?.id}/programming-exercises/${exerciseId}/`;
                this.shortBaseResource = `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}/`;
                this.teamBaseResource =
                    `/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}` +
                    `/exercise-groups/${this.programmingExercise.exerciseGroup?.id}/exercises/${this.programmingExercise.exerciseGroup?.exam?.id}/`;
            }

            this.templateAndSolutionParticipationSubscription = this.programmingExerciseService
                .findWithTemplateAndSolutionParticipationAndLatestResults(programmingExercise.id!)
                .pipe(
                    tap((updatedProgrammingExercise) => {
                        this.programmingExercise = updatedProgrammingExercise.body!;
                        this.loadingTemplateParticipationResults = false;
                        this.loadingSolutionParticipationResults = false;
                    }),
                    mergeMap(() => this.profileService.getProfileInfo()),
                    tap((profileInfo) => {
                        if (profileInfo) {
                            if (this.programmingExercise.projectKey && this.programmingExercise.templateParticipation?.buildPlanId) {
                                this.programmingExercise.templateParticipation.buildPlanUrl = createBuildPlanUrl(
                                    profileInfo.buildPlanURLTemplate,
                                    this.programmingExercise.projectKey,
                                    this.programmingExercise.templateParticipation.buildPlanId,
                                );
                            }
                            if (this.programmingExercise.projectKey && this.programmingExercise.solutionParticipation?.buildPlanId) {
                                this.programmingExercise.solutionParticipation.buildPlanUrl = createBuildPlanUrl(
                                    profileInfo.buildPlanURLTemplate,
                                    this.programmingExercise.projectKey,
                                    this.programmingExercise.solutionParticipation.buildPlanId,
                                );
                            }
                            this.supportsAuxiliaryRepositories =
                                this.programmingLanguageFeatureService.getProgrammingLanguageFeature(programmingExercise.programmingLanguage)?.auxiliaryRepositoriesSupported ??
                                false;
                            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
                            this.localCIEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALCI);
                            this.irisEnabled = profileInfo.activeProfiles.includes(PROFILE_IRIS);
                            if (this.irisEnabled && !this.isExamExercise) {
                                this.irisSettingsSubscription = this.irisSettingsService.getCombinedCourseSettings(this.courseId).subscribe((settings) => {
                                    this.irisChatEnabled = settings?.irisChatSettings?.enabled ?? false;
                                });
                            }
                        }
                    }),
                    mergeMap(() => this.programmingExerciseSubmissionPolicyService.getSubmissionPolicyOfProgrammingExercise(exerciseId)),
                    tap((submissionPolicy) => {
                        this.programmingExercise.submissionPolicy = submissionPolicy;
                    }),
                    mergeMap(() => this.programmingExerciseService.getDiffReport(exerciseId)),
                    catchError(() => {
                        this.alertService.error('artemisApp.programmingExercise.diffReportError');
                        return of(undefined);
                    }),
                    tap((gitDiffReport) => {
                        this.processGitDiffReport(gitDiffReport);
                    }),
                )
                // split pipe to keep type checks
                .pipe(
                    mergeMap(() =>
                        this.programmingExercise.isAtLeastEditor ? this.programmingExerciseService.getBuildLogStatistics(exerciseId!) : of([] as BuildLogStatisticsDTO),
                    ),
                    tap((buildLogStatistics) => {
                        if (this.programmingExercise.isAtLeastEditor) {
                            this.programmingExercise.buildLogStatistics = buildLogStatistics;
                        }
                    }),
                )
                .subscribe({
                    next: () => {
                        this.checkAndAlertInconsistencies();
                        this.plagiarismCheckSupported =
                            this.programmingLanguageFeatureService.getProgrammingLanguageFeature(programmingExercise.programmingLanguage)?.plagiarismCheckSupported ?? false;

                        /** we make sure to await the results of the subscriptions (switchMap) to only call {@link getExerciseDetails} once */
                        this.exerciseDetailSections = this.getExerciseDetails();
                    },
                    error: (error) => {
                        this.alertService.error(error.message);
                    },
                });

            this.exerciseStatisticsSubscription = this.statisticsService.getExerciseStatistics(exerciseId!).subscribe((statistics: ExerciseManagementStatisticsDto) => {
                this.doughnutStats = statistics;
            });
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
        this.activatedRouteSubscription?.unsubscribe();
        this.templateAndSolutionParticipationSubscription?.unsubscribe();
        this.irisSettingsSubscription?.unsubscribe();
        this.exerciseStatisticsSubscription?.unsubscribe();
    }

    /**
     * <strong>BE CAREFUL WHEN CALLING THIS METHOD!</strong><br>
     * This method can cause child components to re-render, which can lead to re-initializations resulting
     * in unnecessary requests putting load on the server.
     *
     * <strong>When adding a new call to this method, make sure that no duplicated and unnecessary requests are made.</strong>
     */
    getExerciseDetails(): DetailOverviewSection[] {
        const exercise = this.programmingExercise;
        exercise.buildConfig = this.programmingExerciseBuildConfig;
        return [
            this.getExerciseDetailsGeneralSection(exercise),
            this.getExerciseDetailsModeSection(exercise),
            this.getExerciseDetailsLanguageSection(exercise),
            this.getExerciseDetailsProblemSection(exercise),
            this.getExerciseDetailsGradingSection(exercise),
        ] as DetailOverviewSection[];
    }

    getExerciseDetailsGeneralSection(exercise: ProgrammingExercise): DetailOverviewSection {
        return {
            headline: 'artemisApp.programmingExercise.wizardMode.detailedSteps.generalInfoStepTitle',
            details: [
                exercise.course && {
                    type: DetailType.Link,
                    title: 'artemisApp.exercise.course',
                    data: { text: exercise.course?.title, routerLink: ['/course-management', exercise.course?.id] },
                },
                exercise.exerciseGroup && {
                    type: DetailType.Link,
                    title: 'artemisApp.exercise.course',
                    data: { text: exercise.exerciseGroup?.exam?.course?.id, routerLink: ['/course-management', exercise.exerciseGroup?.exam?.course?.id] },
                },
                exercise.exerciseGroup && {
                    type: DetailType.Link,
                    title: 'artemisApp.exercise.exam',
                    data: {
                        text: exercise.exerciseGroup?.exam?.title,
                        routerLink: ['/course-management', exercise.exerciseGroup?.exam?.course?.id, 'exams', exercise.exerciseGroup?.exam?.id],
                    },
                },
                { type: DetailType.Text, title: 'artemisApp.exercise.title', data: { text: exercise.title } },
                { type: DetailType.Text, title: 'artemisApp.exercise.shortName', data: { text: exercise.shortName } },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.exercise.categories',
                    data: { text: exercise.categories?.map((category) => category.category?.toUpperCase()).join(', ') },
                },
            ],
        };
    }

    getExerciseDetailsModeSection(exercise: ProgrammingExercise): DetailOverviewSection {
        return {
            headline: 'artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepTitle',
            details: [
                {
                    type: DetailType.Text,
                    title: 'artemisApp.exercise.difficulty',
                    data: { text: exercise.difficulty },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.exercise.mode',
                    data: { text: exercise.mode },
                },
                exercise.teamAssignmentConfig && {
                    type: DetailType.Text,
                    title: 'artemisApp.exercise.teamAssignmentConfig.teamSize',
                    data: { text: `Min. ${exercise.teamAssignmentConfig.minTeamSize}, Max. ${exercise.teamAssignmentConfig.maxTeamSize}` },
                },
                {
                    type: DetailType.Boolean,
                    title: 'artemisApp.programmingExercise.allowOfflineIde.title',
                    data: { boolean: exercise.allowOfflineIde ?? false },
                },
                {
                    type: DetailType.Boolean,
                    title: 'artemisApp.programmingExercise.allowOnlineEditor.title',
                    data: { boolean: exercise.allowOnlineEditor ?? false },
                },
                {
                    type: DetailType.Boolean,
                    title: 'artemisApp.programmingExercise.allowOnlineIde.title',
                    data: { boolean: exercise.allowOnlineIde ?? false },
                },
            ],
        };
    }

    getExerciseDetailsLanguageSection(exercise: ProgrammingExercise): DetailOverviewSection {
        this.checkAndSetWindFile(exercise);
        return {
            headline: 'artemisApp.programmingExercise.wizardMode.detailedSteps.languageStepTitle',
            details: [
                {
                    type: DetailType.Text,
                    title: 'artemisApp.programmingExercise.programmingLanguage',
                    data: { text: exercise.programmingLanguage?.toUpperCase() },
                },
                {
                    type: DetailType.Boolean,
                    title: 'artemisApp.programmingExercise.sequentialTestRuns.title',
                    data: { boolean: exercise.buildConfig?.sequentialTestRuns },
                },
                {
                    type: DetailType.ProgrammingRepositoryButtons,
                    title: 'artemisApp.programmingExercise.templateRepositoryUri',
                    data: {
                        participation: exercise.templateParticipation,
                        exerciseId: exercise.id,
                        type: RepositoryType.TEMPLATE,
                    },
                },
                {
                    type: DetailType.ProgrammingRepositoryButtons,
                    title: 'artemisApp.programmingExercise.solutionRepositoryUri',
                    data: {
                        participation: exercise.solutionParticipation,
                        exerciseId: exercise.id,
                        type: RepositoryType.SOLUTION,
                    },
                },
                {
                    type: DetailType.ProgrammingRepositoryButtons,
                    title: 'artemisApp.programmingExercise.testRepositoryUri',
                    data: {
                        participation: { repositoryUri: exercise.testRepositoryUri },
                        exerciseId: exercise.id,
                        type: RepositoryType.TESTS,
                    },
                },
                this.supportsAuxiliaryRepositories &&
                    !!exercise.auxiliaryRepositories?.length && {
                        type: DetailType.ProgrammingAuxiliaryRepositoryButtons,
                        title: 'artemisApp.programmingExercise.auxiliaryRepositories',
                        data: {
                            auxiliaryRepositories: exercise.auxiliaryRepositories,
                            exerciseId: exercise.id,
                        },
                    },
                exercise.isAtLeastEditor &&
                    this.localCIEnabled && {
                        type: DetailType.ProgrammingCheckoutDirectories,
                        title: 'artemisApp.programmingExercise.checkoutDirectories',
                        data: {
                            exercise: exercise,
                            programmingLanguage: exercise.programmingLanguage,
                            isLocal: true,
                        },
                    },
                !this.localCIEnabled && {
                    type: DetailType.Link,
                    title: 'artemisApp.programmingExercise.templateBuildPlanId',
                    data: {
                        href: exercise.templateParticipation?.buildPlanUrl,
                        text: exercise.templateParticipation?.buildPlanId,
                    },
                },
                !this.localCIEnabled && {
                    type: DetailType.Link,
                    title: 'artemisApp.programmingExercise.solutionBuildPlanId',
                    data: {
                        href: exercise.solutionParticipation?.buildPlanUrl,
                        text: exercise.solutionParticipation?.buildPlanId,
                    },
                },
                {
                    type: DetailType.ProgrammingTestStatus,
                    title: 'artemisApp.programmingExercise.templateResult',
                    data: {
                        exercise,
                        participation: exercise.templateParticipation,
                        loading: this.loadingTemplateParticipationResults,
                        submissionRouterLink: exercise.templateParticipation && this.getParticipationSubmissionLink(exercise.templateParticipation.id!),
                        onParticipationChange: () => this.onParticipationChange(),
                        type: ProgrammingExerciseParticipationType.TEMPLATE,
                    },
                },
                {
                    type: DetailType.ProgrammingTestStatus,
                    title: 'artemisApp.programmingExercise.solutionResult',
                    data: {
                        exercise,
                        participation: exercise.solutionParticipation,
                        loading: this.loadingSolutionParticipationResults,
                        submissionRouterLink: exercise.solutionParticipation && this.getParticipationSubmissionLink(exercise.solutionParticipation.id!),
                        onParticipationChange: () => this.onParticipationChange(),
                        type: ProgrammingExerciseParticipationType.SOLUTION,
                    },
                },
                this.addedLineCount !== undefined &&
                    this.removedLineCount !== undefined && {
                        type: DetailType.ProgrammingDiffReport,
                        title: 'artemisApp.programmingExercise.diffReport.title',
                        titleHelpText: 'artemisApp.programmingExercise.diffReport.detailedTooltip',
                        data: {
                            addedLineCount: this.addedLineCount,
                            removedLineCount: this.removedLineCount,
                            isLoadingDiffReport: this.isLoadingDiffReport,
                            gitDiffReport: exercise.gitDiffReport,
                        },
                    },
                !!exercise.buildConfig?.buildScript &&
                    !!exercise.buildConfig?.windfile?.metadata?.docker?.image && {
                        type: DetailType.Text,
                        title: 'artemisApp.programmingExercise.dockerImage',
                        data: { text: exercise.buildConfig?.windfile?.metadata?.docker?.image },
                    },
                !!exercise.buildConfig?.buildScript &&
                    !!exercise.buildConfig?.windfile?.metadata?.docker?.image && {
                        type: DetailType.Markdown,
                        title: 'artemisApp.programmingExercise.script',
                        titleHelpText: 'artemisApp.programmingExercise.revertToTemplateBuildPlan',
                        data: { innerHtml: this.artemisMarkdown.safeHtmlForMarkdown('```bash\n' + exercise.buildConfig?.buildScript + '\n```') },
                    },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.programmingExercise.packageName',
                    data: { text: exercise.packageName },
                },
            ],
        };
    }

    getExerciseDetailsProblemSection(exercise: ProgrammingExercise): DetailOverviewSection {
        const hasCompetencies = !!this.competencies?.length;
        const details: Detail[] = [
            {
                title: hasCompetencies ? 'artemisApp.programmingExercise.wizardMode.detailedSteps.problemStepTitle' : undefined,
                type: DetailType.ProgrammingProblemStatement,
                data: { exercise: exercise },
            },
        ];

        if (hasCompetencies) {
            details.push({
                title: 'artemisApp.competency.link.title',
                type: DetailType.Text,
                data: { text: this.competencies?.map((competency) => competency.title).join(', ') },
            });
        }

        return {
            headline: 'artemisApp.programmingExercise.wizardMode.detailedSteps.problemStepTitle',
            details: details,
        };
    }

    getExerciseDetailsGradingSection(exercise: ProgrammingExercise): DetailOverviewSection {
        const includedInScoreIsBoolean = exercise.includedInOverallScore != IncludedInOverallScore.INCLUDED_AS_BONUS;
        const includedInScore: Detail = includedInScoreIsBoolean
            ? {
                  type: DetailType.Boolean,
                  title: 'artemisApp.exercise.includedInOverallScore',
                  data: { boolean: exercise.includedInOverallScore === IncludedInOverallScore.INCLUDED_COMPLETELY },
              }
            : {
                  type: DetailType.Text,
                  title: 'artemisApp.exercise.includedInOverallScore',
                  data: { text: 'BONUS' },
              };
        return {
            headline: 'artemisApp.programmingExercise.wizardMode.detailedSteps.gradingStepTitle',
            details: [
                { type: DetailType.Text, title: 'artemisApp.exercise.points', data: { text: exercise.maxPoints } },
                !!exercise.bonusPoints && { type: DetailType.Text, title: 'artemisApp.exercise.bonusPoints', data: { text: exercise.bonusPoints } },
                includedInScore,
                { type: DetailType.Boolean, title: 'artemisApp.exercise.presentationScoreEnabled.title', data: { boolean: exercise.presentationScoreEnabled } },
                { type: DetailType.Boolean, title: 'artemisApp.programmingExercise.enableStaticCodeAnalysis.title', data: { boolean: exercise.staticCodeAnalysisEnabled } },
                exercise.staticCodeAnalysisEnabled && {
                    type: DetailType.Text,
                    title: 'artemisApp.programmingExercise.maxStaticCodeAnalysisPenalty.title',
                    data: { text: exercise.maxStaticCodeAnalysisPenalty },
                },
                {
                    type: DetailType.Text,
                    title: 'artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.title',
                    data: {
                        text: this.translateService.instant(
                            'artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.' +
                                (!exercise.submissionPolicy ? 'none' : exercise.submissionPolicy.type!) +
                                '.title',
                        ),
                    },
                },
                exercise.submissionPolicy && {
                    type: DetailType.Text,
                    title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle',
                    data: { text: exercise.submissionPolicy.submissionLimit },
                },
                exercise.submissionPolicy &&
                    !!exercise.submissionPolicy.exceedingPenalty && {
                        type: DetailType.Text,
                        title: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.detailLabel',
                        data: { text: exercise.submissionPolicy.exceedingPenalty },
                    },
                { type: DetailType.ProgrammingTimeline, title: 'artemisApp.programmingExercise.timeline.timelineLabel', data: { exercise, isExamMode: this.isExamExercise } },
                {
                    type: DetailType.Boolean,
                    title: 'artemisApp.programmingExercise.timeline.complaintOnAutomaticAssessment',
                    data: { boolean: exercise.allowComplaintsForAutomaticAssessments },
                },
                { type: DetailType.Boolean, title: 'artemisApp.programmingExercise.timeline.manualFeedbackRequests', data: { boolean: exercise.allowFeedbackRequests } },
                { type: DetailType.Boolean, title: 'artemisApp.programmingExercise.showTestNamesToStudents', data: { boolean: exercise.showTestNamesToStudents } },
                {
                    type: DetailType.Boolean,
                    title: 'artemisApp.programmingExercise.timeline.releaseTestsWithExampleSolution',
                    data: { boolean: exercise.releaseTestsWithExampleSolution },
                },
                { type: DetailType.Boolean, title: 'artemisApp.exercise.feedbackSuggestionsEnabled', data: { boolean: !!exercise.feedbackSuggestionModule } },
                { type: DetailType.Markdown, title: 'artemisApp.exercise.assessmentInstructions', data: { innerHtml: this.formattedGradingInstructions } },
                exercise.gradingCriteria && {
                    type: DetailType.GradingCriteria,
                    title: 'artemisApp.exercise.structuredAssessmentInstructions',
                    data: { gradingCriteria: exercise.gradingCriteria },
                },
                this.irisEnabled &&
                    this.irisChatEnabled &&
                    exercise.course &&
                    !this.isExamExercise && {
                        type: DetailType.ProgrammingIrisEnabled,
                        title: 'artemisApp.iris.settings.subSettings.enabled.chat',
                        data: { exercise, disabled: !exercise.isAtLeastInstructor, subSettingsType: IrisSubSettingsType.CHAT },
                    },
                exercise.buildLogStatistics && {
                    type: DetailType.ProgrammingBuildStatistics,
                    title: 'artemisApp.programmingExercise.buildLogStatistics.title',
                    titleHelpText: 'artemisApp.programmingExercise.buildLogStatistics.tooltip',
                    data: { buildLogStatistics: exercise.buildLogStatistics },
                },
            ],
        };
    }

    private checkBuildPlanEditable() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => (this.isBuildPlanEditable = hasEditableBuildPlan(profileInfo)));
    }

    onParticipationChange(): void {
        this.loadGitDiffReport();
    }

    combineTemplateCommits() {
        this.programmingExerciseService.combineTemplateRepositoryCommits(this.programmingExercise.id!).subscribe({
            next: () => {
                this.alertService.success('artemisApp.programmingExercise.combineTemplateCommitsSuccess');
            },
            error: () => {
                this.alertService.error('artemisApp.programmingExercise.combineTemplateCommitsError');
            },
        });
    }

    generateStructureOracle() {
        this.programmingExerciseService.generateStructureOracle(this.programmingExercise.id!).subscribe({
            next: (res) => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: res,
                    disableTranslation: true,
                });
            },
            error: (error) => {
                const errorMessage = error.headers.get('X-artemisApp-alert');
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: errorMessage,
                    disableTranslation: true,
                });
            },
        });
    }

    deleteProgrammingExercise(event: { [key: string]: boolean }) {
        this.programmingExerciseService.delete(this.programmingExercise.id!, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted a programming exercise',
                });
                this.dialogErrorSource.next('');

                if (!this.isExamExercise) {
                    this.router.navigateByUrl(`/course-management/${this.courseId}/exercises`);
                } else {
                    this.router.navigateByUrl(`/course-management/${this.courseId}/exams/${this.programmingExercise.exerciseGroup?.exam?.id}/exercise-groups`);
                }
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Opens modal and executes a consistency check for the given programming exercise
     * @param exercise the programming exercise to check
     */
    checkConsistencies(exercise: ProgrammingExercise) {
        const modalRef = this.modalService.open(ConsistencyCheckComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.exercisesToCheck = Array.of(exercise);
    }

    /**
     * Executes a consistency check for this programming exercise and alerts the user if any inconsistencies are found
     * This is only run if the user is at least an instructor in the course
     */
    checkAndAlertInconsistencies() {
        if (this.programmingExercise.isAtLeastEditor) {
            this.consistencyCheckService.checkConsistencyForProgrammingExercise(this.programmingExercise.id!).subscribe((inconsistencies) => {
                if (inconsistencies.length) {
                    this.alertService.warning('artemisApp.consistencyCheck.inconsistenciesFoundAlert');
                }
            });
        }
    }

    /**
     * Checks if the build configuration is available and sets the windfile if it is, helpful for reliably displaying
     * the build configuration in the UI
     * @param exercise the programming exercise to check
     */
    checkAndSetWindFile(exercise: ProgrammingExercise) {
        if (exercise.buildConfig && exercise.buildConfig?.buildPlanConfiguration && !exercise.buildConfig?.windfile) {
            exercise.buildConfig!.windfile = this.aeolusService.parseWindFile(exercise.buildConfig?.buildPlanConfiguration);
        }
    }

    /**
     * Generates the link to any participation's submissions, used for the link to template and solution submissions
     * @param participationId of the participation
     */
    getParticipationSubmissionLink(participationId: number) {
        const link = [this.baseResource, 'participations', participationId];
        // For unknown reason normal exercises append /submissions to the submission view whereas exam exercises do not
        if (!this.isExamExercise) {
            link.push('submissions');
        }
        return link;
    }

    /**
     * Calculates the added and removed lines of the diff
     * @param gitDiffReport
     * @returns whether the report has changed compared to the last run
     */
    private processGitDiffReport(gitDiffReport: ProgrammingExerciseGitDiffReport | undefined): boolean {
        const isGitDiffReportUpdated =
            gitDiffReport &&
            (this.programmingExercise.gitDiffReport?.templateRepositoryCommitHash !== gitDiffReport.templateRepositoryCommitHash ||
                this.programmingExercise.gitDiffReport?.solutionRepositoryCommitHash !== gitDiffReport.solutionRepositoryCommitHash);
        if (!isGitDiffReportUpdated) {
            return false;
        }

        this.programmingExercise.gitDiffReport = gitDiffReport;
        gitDiffReport.programmingExercise = this.programmingExercise;
        const calculateLineCount = (
            entries: {
                lineCount?: number;
                previousLineCount?: number;
            }[] = [],
            key: 'lineCount' | 'previousLineCount',
        ) => entries.map((entry) => entry[key] ?? 0).reduce((sum, count) => sum + count, 0);
        this.addedLineCount = calculateLineCount(gitDiffReport.entries, 'lineCount');
        this.removedLineCount = calculateLineCount(gitDiffReport.entries, 'previousLineCount');

        return true;
    }

    loadGitDiffReport() {
        this.programmingExerciseService.getDiffReport(this.programmingExercise.id!).subscribe({
            next: (gitDiffReport) => {
                this.processGitDiffReport(gitDiffReport);
            },
            error: () => {
                this.alertService.error('artemisApp.programmingExercise.diffReportError');
            },
        });
    }
}
