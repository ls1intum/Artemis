import { Component, OnDestroy, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { Observable, Subject, Subscription, forkJoin, from, of } from 'rxjs';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
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
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { MODULE_FEATURE_PLAGIARISM, PROFILE_IRIS, PROFILE_LOCALCI } from 'app/app.constants';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';
import { Detail } from 'app/shared/detail-overview-list/detail.model';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { AeolusService } from 'app/programming/shared/services/aeolus.service';
import { catchError, map, mergeMap, switchMap, tap } from 'rxjs/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ProgrammingExerciseInstructorExerciseDownloadComponent } from 'app/programming/shared/actions/instructor-exercise-download/programming-exercise-instructor-exercise-download.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { ProgrammingExerciseResetButtonDirective } from 'app/programming/manage/reset/button/programming-exercise-reset-button.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RepositoryType } from '../../shared/code-editor/model/code-editor.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ConsistencyCheckComponent } from 'app/programming/manage/consistency-check/consistency-check.component';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { RepositoryDiffInformation, processRepositoryDiff } from 'app/programming/shared/utils/diff.utils';
import { ProgrammingExerciseInstructorExerciseSharingComponent } from '../../shared/actions/programming-exercise-instructor-exercise-sharing.component';
import { ProgrammingExerciseSharingService } from '../services/programming-exercise-sharing.service';

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
        FeatureOverlayComponent,
        ProgrammingExerciseInstructorExerciseSharingComponent,
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
    repositoryDiffInformation: RepositoryDiffInformation;
    templateFileContentByPath: Map<string, string>;
    solutionFileContentByPath: Map<string, string>;
    competencies: Competency[];
    isExamExercise: boolean;
    supportsAuxiliaryRepositories: boolean;
    baseResource: string;
    shortBaseResource: string;
    teamBaseResource: string;
    loadingTemplateParticipationResults = true;
    loadingSolutionParticipationResults = true;
    diffReady = false;
    courseId: number;
    doughnutStats: ExerciseManagementStatisticsDto;
    formattedGradingInstructions: SafeHtml;
    localCIEnabled = true;
    irisEnabled = false;
    irisChatEnabled = false;
    plagiarismEnabled = false;

    isExportToSharingEnabled = false;

    isAdmin = false;
    isBuildPlanEditable = false;

    plagiarismCheckSupported = false; // default value

    private activatedRouteSubscription: Subscription;
    private templateAndSolutionParticipationSubscription: Subscription;
    private irisSettingsSubscription: Subscription;
    private exerciseStatisticsSubscription: Subscription;
    private sharingEnabledSubscription: Subscription;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    exerciseDetailSections: DetailOverviewSection[];

    private lastUpdateTime = 0;
    private readonly UPDATE_DEBOUNCE_MS = 1000;

    constructor(private sharingService: ProgrammingExerciseSharingService) {}

    ngOnInit() {
        this.isBuildPlanEditable = this.profileService.isProfileActive('jenkins');

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
                    tap(() => {
                        this.localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
                        this.irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
                        const profileInfo = this.profileService.getProfileInfo();
                        if (this.programmingExercise.projectKey && this.programmingExercise.templateParticipation?.buildPlanId && profileInfo.buildPlanURLTemplate) {
                            this.programmingExercise.templateParticipation.buildPlanUrl = createBuildPlanUrl(
                                profileInfo.buildPlanURLTemplate,
                                this.programmingExercise.projectKey,
                                this.programmingExercise.templateParticipation.buildPlanId,
                            );
                        }
                        if (this.programmingExercise.projectKey && this.programmingExercise.solutionParticipation?.buildPlanId && profileInfo.buildPlanURLTemplate) {
                            this.programmingExercise.solutionParticipation.buildPlanUrl = createBuildPlanUrl(
                                profileInfo.buildPlanURLTemplate,
                                this.programmingExercise.projectKey,
                                this.programmingExercise.solutionParticipation.buildPlanId,
                            );
                        }
                        this.supportsAuxiliaryRepositories =
                            this.programmingLanguageFeatureService.getProgrammingLanguageFeature(programmingExercise.programmingLanguage)?.auxiliaryRepositoriesSupported ?? false;
                        if (this.irisEnabled && !this.isExamExercise) {
                            this.irisSettingsSubscription = this.irisSettingsService.getCombinedCourseSettings(this.courseId).subscribe((settings) => {
                                this.irisChatEnabled = settings?.irisProgrammingExerciseChatSettings?.enabled ?? false;
                            });
                        }
                        this.plagiarismEnabled = profileInfo.activeModuleFeatures.includes(MODULE_FEATURE_PLAGIARISM);
                    }),
                    mergeMap(() => this.programmingExerciseSubmissionPolicyService.getSubmissionPolicyOfProgrammingExercise(exerciseId)),
                    tap((submissionPolicy) => {
                        this.programmingExercise.submissionPolicy = submissionPolicy;
                    }),
                    mergeMap(() => this.fetchRepositoryFiles()),
                    catchError(() => {
                        this.alertService.error('artemisApp.programmingExercise.repositoryFilesError');
                        return of({ templateFiles: new Map<string, string>(), solutionFiles: new Map<string, string>() });
                    }),
                    switchMap(({ templateFiles, solutionFiles }: { templateFiles: Map<string, string> | undefined; solutionFiles: Map<string, string> | undefined }) => {
                        return from(this.handleDiff(templateFiles, solutionFiles));
                    }),
                )
                // split pipe to keep type checks
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
        this.sharingEnabledSubscription = this.sharingService
            .isSharingEnabled()
            .pipe(
                map((response) => response.body ?? false),
                catchError(() => {
                    return of(false);
                }),
            )
            .subscribe((isEnabled) => {
                this.isExportToSharingEnabled = isEnabled;
            });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
        this.activatedRouteSubscription?.unsubscribe();
        this.templateAndSolutionParticipationSubscription?.unsubscribe();
        this.irisSettingsSubscription?.unsubscribe();
        this.exerciseStatisticsSubscription?.unsubscribe();
        this.sharingEnabledSubscription?.unsubscribe();
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
                this.repositoryDiffInformation &&
                    this.templateFileContentByPath &&
                    this.solutionFileContentByPath &&
                    this.diffReady && {
                        type: DetailType.ProgrammingDiffReport,
                        title: 'artemisApp.programmingExercise.diffReport.title',
                        titleHelpText: 'artemisApp.programmingExercise.diffReport.detailedTooltip',
                        data: {
                            repositoryDiffInformation: this.repositoryDiffInformation,
                            templateFileContentByPath: this.templateFileContentByPath,
                            solutionFileContentByPath: this.solutionFileContentByPath,
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
                        data: { exercise, disabled: !exercise.isAtLeastInstructor, subSettingsType: IrisSubSettingsType.PROGRAMMING_EXERCISE_CHAT },
                    },
            ],
        };
    }

    onParticipationChange(): void {
        // Debounce rapid successive calls to prevent infinite loops
        const now = Date.now();
        if (now - this.lastUpdateTime < this.UPDATE_DEBOUNCE_MS) {
            return;
        }

        const previousDiffInfo = this.repositoryDiffInformation;
        const previousTemplateFiles = this.templateFileContentByPath;
        const previousSolutionFiles = this.solutionFileContentByPath;

        this.fetchRepositoryFiles()
            .pipe(
                switchMap(({ templateFiles, solutionFiles }) => {
                    return from(this.handleDiff(templateFiles, solutionFiles));
                }),
                tap(() => {
                    // Update exercise details if any diff-related data has actually changed
                    const diffDataChanged =
                        this.repositoryDiffInformation !== previousDiffInfo ||
                        this.templateFileContentByPath !== previousTemplateFiles ||
                        this.solutionFileContentByPath !== previousSolutionFiles;

                    if (diffDataChanged) {
                        this.exerciseDetailSections = this.getExerciseDetails();
                        this.lastUpdateTime = Date.now();
                    }
                }),
            )
            .subscribe({
                error: (error) => {
                    this.alertService.error('artemisApp.programmingExercise.participationChangeError');
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

    fetchRepositoryFiles(): Observable<{ templateFiles: Map<string, string> | undefined; solutionFiles: Map<string, string> | undefined }> {
        return forkJoin({
            templateFiles: this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.programmingExercise.id!),
            solutionFiles: this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.programmingExercise.id!),
        });
    }

    async handleDiff(templateFiles: Map<string, string> | undefined, solutionFiles: Map<string, string> | undefined) {
        if (!templateFiles || !solutionFiles) {
            return;
        }

        try {
            this.templateFileContentByPath = templateFiles;
            this.solutionFileContentByPath = solutionFiles;
            this.repositoryDiffInformation = await processRepositoryDiff(templateFiles, solutionFiles);
            // Set ready state to true when diff processing is complete
            this.diffReady = true;
        } catch (error) {
            this.alertService.error('artemisApp.programmingExercise.diffProcessingError');
            // Reset to a consistent state
            this.diffReady = false;
            this.repositoryDiffInformation = {
                diffInformations: [],
                totalLineChange: {
                    addedLineCount: 0,
                    removedLineCount: 0,
                },
            };
        }
    }
}
