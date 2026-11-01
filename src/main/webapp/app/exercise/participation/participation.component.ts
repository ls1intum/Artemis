import { Component, OnDestroy, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { Subject, Subscription } from 'rxjs';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ParticipationService } from './participation.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { HttpErrorResponse } from '@angular/common/http';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { InitializationState, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { User } from 'app/account/user/user.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AlertService } from 'app/foundation/service/alert.service';
import {
    faCheck,
    faChevronDown,
    faCircleInfo,
    faCircleNotch,
    faComment,
    faDownload,
    faEraser,
    faFilePowerpoint,
    faFolderOpen,
    faPencil,
    faSync,
    faTimes,
    faTrash,
} from '@fortawesome/free-solid-svg-icons';
import { faFileCode } from '@fortawesome/free-regular-svg-icons';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ProgrammingExerciseInstructorSubmissionStateComponent } from 'app/programming/shared/actions/instructor-submission-state/programming-exercise-instructor-submission-state.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import {
    CellTemplateRef,
    ColumnDef,
    TumAetUiButtonDirective,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiPopoverComponent,
    TumAetUiPopoverTriggerDirective,
    TumAetUiTableComponent,
    TumAetUiTableQueryEvent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ParticipationManagementDTO } from './participation-management-dto.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { ParticipationSearch } from 'app/foundation/pagination/pageable-table';
import { FilterDropdownComponent, FilterGroup } from 'app/exercise/shared/filter-dropdown/filter-dropdown.component';
import { TeamStudentsListComponent } from 'app/exercise/team/team-participate/team-students-list.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { ParticipationPrivacyService } from 'app/exercise/participation/participation-privacy.service';
import { ParticipationAccessNoticeComponent } from 'app/exercise/participation/participation-access-notice.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { areManualResultsAllowed } from 'app/exercise/util/exercise.utils';
import { Range } from 'app/foundation/util/utils';
import { onError } from 'app/foundation/util/global.utils';
import { ResultService } from 'app/exercise/result/result.service';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';
import { ExternalSubmissionButtonComponent } from 'app/exercise/external-submission/external-submission-button.component';
import { ExerciseScoresExportButtonComponent } from 'app/exercise/exercise-scores/export-button/exercise-scores-export-button.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/programming/manage/assess/repo-export/export-button/programming-assessment-repo-export-button.component';
import { SubmissionExportButtonComponent } from 'app/exercise/submission-export/button/submission-export-button.component';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { ManageAssessmentButtonsComponent } from 'app/exercise/exercise-scores/manage-assessment-buttons/manage-assessment-buttons.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';

export enum FilterProp {
    FAILED = 'Failed',
    NO_SUBMISSIONS = 'NoSubmissions',
    VALID_SUBMISSIONS = 'ValidSubmissions',
    NO_PRACTICE_MODE = 'NoPracticeMode',
    GRADED = 'Graded',
    SUCCESSFUL = 'Successful',
    UNSUCCESSFUL = 'Unsuccessful',
    BUILD_FAILED = 'BuildFailed',
    MANUAL = 'Manual',
    SEMI_AUTOMATIC = 'SemiAutomatic',
    AUTOMATIC = 'Automatic',
    LOCKED = 'Locked',
}

@Component({
    selector: 'jhi-participation',
    templateUrl: './participation.component.html',
    providers: [ExerciseCacheService],
    imports: [
        TranslateDirective,
        ParticipationAccessNoticeComponent,
        FormsModule,
        ProgrammingExerciseInstructorSubmissionStateComponent,
        RouterLink,
        FaIconComponent,
        TumAetUiTableComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
        TumAetUiTooltipDirective,
        CodeButtonComponent,
        TumAetUiDatePickerComponent,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        DeleteButtonDirective,
        FeatureToggleDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        FilterDropdownComponent,
        TeamStudentsListComponent,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        TumAetUiTagComponent,
        ExternalSubmissionButtonComponent,
        ExerciseScoresExportButtonComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        SubmissionExportButtonComponent,
        FeatureToggleLinkDirective,
        ManageAssessmentButtonsComponent,
        ResultComponent,
        ArtemisDurationFromSecondsPipe,
    ],
})
export class ParticipationComponent implements OnInit, OnDestroy {
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly resultService = inject(ResultService);
    private readonly privacyService = inject(ParticipationPrivacyService);
    private readonly participationService = inject(ParticipationService);
    private readonly alertService = inject(AlertService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly programmingSubmissionService = inject(ProgrammingSubmissionService);
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);
    private readonly gradingService = inject(GradingService);
    private readonly websocketService = inject(WebsocketService);

    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faComment = faComment;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faDownload = faDownload;
    protected readonly faSync = faSync;
    protected readonly faFolderOpen = faFolderOpen;
    protected readonly farFileCode = faFileCode;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faEraser = faEraser;
    protected readonly faFilePowerpoint = faFilePowerpoint;
    protected readonly faPencil = faPencil;
    protected readonly faCheck = faCheck;

    protected FilterProp = FilterProp;

    protected readonly ExerciseType = ExerciseType;
    protected readonly ActionType = ActionType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly RepositoryType = RepositoryType;

    readonly exercise = signal<Exercise | undefined>(undefined);
    // The assessment links only need the course id, which the route carries for course and exam exercises alike
    readonly course = signal<Course | undefined>(undefined);
    readonly newManualResultAllowed = signal(false);

    // represents all intervals selectable in the score distribution on the exercise statistics
    readonly scoreRanges = [
        new Range(0, 10),
        new Range(10, 20),
        new Range(20, 30),
        new Range(30, 40),
        new Range(40, 50),
        new Range(50, 60),
        new Range(60, 70),
        new Range(70, 80),
        new Range(80, 90),
        new Range(90, 100),
    ];
    readonly rangeFilter = signal<Range | undefined>(undefined);
    readonly hideParticipantInformation = computed(() => this.privacyService.isParticipantInformationHidden(this.exercise()));
    /** Only editors and instructors may open the editable student repository. */
    readonly canAccessLiveRepository = computed(() => !!this.exercise()?.isAtLeastEditor || !!this.exercise()?.isAtLeastInstructor);
    readonly participations = signal<ParticipationManagementDTO[]>([]);
    readonly totalRows = signal(0);
    readonly isLoading = signal(false);
    readonly isSaving = signal(false);
    readonly activeFilters = signal<FilterProp[]>([]);
    readonly hasLoadedPendingSubmissions = signal(false);
    readonly isAdmin = signal(false);
    readonly isLocalCIEnabled = signal(true);
    readonly gradeStepsDTO = signal<GradeStepsDTO | undefined>(undefined);

    readonly basicPresentationEnabled = computed(() => {
        const ex = this.exercise();
        return !!(ex?.isAtLeastTutor === true && (ex?.course?.presentationScore ?? 0) > 0 && ex?.presentationScoreEnabled === true);
    });

    readonly gradedPresentationEnabled = computed(() => {
        const ex = this.exercise();
        return !!(ex?.course && ex?.isAtLeastTutor && (this.gradeStepsDTO()?.presentationsNumber ?? 0) > 0 && ex?.presentationScoreEnabled === true);
    });

    readonly filterGroups = computed<FilterGroup[]>(() => {
        const ex = this.exercise();
        if (!ex) return [];
        const isProgramming = ex.type === ExerciseType.PROGRAMMING;
        const assessmentTypeFilterable = isProgramming || ex.type === ExerciseType.TEXT || ex.type === ExerciseType.MODELING;
        const manualAssessmentEnabled = ex.type !== ExerciseType.QUIZ && (!isProgramming || ex.assessmentType !== AssessmentType.AUTOMATIC);
        return [
            {
                labelKey: 'artemisApp.participation.filterGroup.submissions',
                single: !isProgramming,
                items: [...(isProgramming ? [FilterProp.FAILED, FilterProp.BUILD_FAILED] : []), FilterProp.NO_SUBMISSIONS, FilterProp.VALID_SUBMISSIONS],
            },
            { labelKey: 'artemisApp.participation.filterGroup.results', items: [FilterProp.SUCCESSFUL, FilterProp.UNSUCCESSFUL], single: true },
            ...(assessmentTypeFilterable
                ? [
                      {
                          labelKey: 'artemisApp.participation.filterGroup.assessmentType',
                          items: [FilterProp.MANUAL, FilterProp.SEMI_AUTOMATIC, ...(isProgramming ? [FilterProp.AUTOMATIC] : [])],
                          single: true,
                      },
                  ]
                : []),
            ...(isProgramming ? [{ labelKey: 'artemisApp.participation.filterGroup.mode', items: [FilterProp.NO_PRACTICE_MODE] }] : []),
            ...(isProgramming || ex.type === ExerciseType.QUIZ || ex.type === ExerciseType.TEXT || ex.type === ExerciseType.MODELING
                ? [{ labelKey: 'artemisApp.participation.filterGroup.gradingStatus', items: [FilterProp.GRADED] }]
                : []),
            ...(manualAssessmentEnabled && ex.isAtLeastInstructor ? [{ labelKey: 'artemisApp.participation.filterGroup.assessmentStatus', items: [FilterProp.LOCKED] }] : []),
        ];
    });

    readonly isExamExercise = computed(() => !!this.exercise()?.exerciseGroup);

    private lastQuery: TumAetUiTableQueryEvent | undefined;
    private currentLoadRequestId = 0;
    // private exerciseSubmissionState: ExerciseSubmissionState = {};
    private paramSub?: Subscription;
    private gradeStepsDTOSub?: Subscription;
    private websocketSubscriptions: Subscription[] = [];
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    // Track graded presentation edits (not signals — not rendered directly)
    participationsChangedPresentation = new Map<number, ParticipationManagementDTO>();

    // Track individual due date inline editing
    readonly editingDueDateIds = signal<ReadonlySet<number>>(new Set());
    private readonly pendingDueDates = new Map<number, dayjs.Dayjs | undefined>();
    /** Rows whose due date text is not a date: the picker keeps its last valid date then, so it cannot be told from the pending date. */
    private readonly invalidDueDateTextIds = signal<ReadonlySet<number>>(new Set());

    readonly tableOptions = computed(() => ({
        showSearch: !this.hideParticipantInformation(),
        searchPlaceholder: this.exercise()?.teamMode ? 'artemisApp.participation.searchTeams' : 'artemisApp.participation.searchStudents',
    }));

    // Template refs
    readonly repositoryCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('repositoryCellTemplate');
    readonly initStateCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('initStateCellTemplate');
    readonly initDateCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('initDateCellTemplate');
    readonly submissionCountCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('submissionCountCellTemplate');
    readonly participantNameCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('participantNameCellTemplate');
    readonly basicPresentationCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('basicPresentationCellTemplate');
    readonly gradedPresentationCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('gradedPresentationCellTemplate');
    readonly individualDueDateCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('individualDueDateCellTemplate');
    readonly completionDateCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('completionDateCellTemplate');
    readonly lastResultCellTemplate = viewChild<CellTemplateRef<ParticipationManagementDTO>>('lastResultCellTemplate');
    readonly exportPopover = viewChild<TumAetUiPopoverComponent>('exportPopover');

    /** Whether the result cell names how the result was produced; only meaningful where manual results can exist. */
    readonly showAssessmentType = computed(() => this.newManualResultAllowed() || !!this.exercise()?.allowComplaintsForAutomaticAssessments);

    /** Whether the result cell offers the assessor's internal note. */
    readonly showAssessmentNote = computed(() => {
        const assessmentType = this.exercise()?.assessmentType;
        return assessmentType === AssessmentType.MANUAL || assessmentType === AssessmentType.SEMI_AUTOMATIC;
    });

    /**
     * One row per participation, read from left to right: who, where their work is, how far they got, what they
     * achieved, and the per-participation settings. Facts that belong together share a cell (team members with the
     * team, practice mode with the status, assessment type and note with the result, duration with the completion
     * date), so the table stays narrow without hiding anything.
     */
    readonly columns = computed<ColumnDef<ParticipationManagementDTO>[]>(() => {
        const ex = this.exercise();
        if (!ex) return [];

        const cols: ColumnDef<ParticipationManagementDTO>[] = [];

        if (this.hideParticipantInformation()) {
            cols.push({ headerKey: 'artemisApp.participation.participationId', field: 'participationId', width: '150px', sort: true });
        } else {
            cols.push({
                headerKey: ex.teamMode ? 'artemisApp.participation.team' : 'artemisApp.participation.student',
                field: 'participantName',
                width: ex.teamMode ? '200px' : '150px',
                sort: true,
                templateRef: this.participantNameCellTemplate(),
            });
        }

        if (ex.type === ExerciseType.PROGRAMMING && !this.hideParticipantInformation()) {
            cols.push({
                headerKey: 'artemisApp.participation.repository',
                width: '80px',
                sort: false,
                templateRef: this.repositoryCellTemplate(),
            });
        }

        cols.push(
            {
                headerKey: 'artemisApp.participation.initializationState',
                field: 'initializationState',
                width: '110px',
                sort: true,
                templateRef: this.initStateCellTemplate(),
            },
            {
                headerKey: 'artemisApp.participation.initializationDate',
                field: 'initializationDate',
                width: '160px',
                sort: true,
                templateRef: this.initDateCellTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.lastResult',
                field: 'score',
                width: '200px',
                sort: true,
                templateRef: this.lastResultCellTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.completionDate',
                field: 'completionDate',
                width: '160px',
                sort: true,
                templateRef: this.completionDateCellTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.submissionCount',
                field: 'submissionCount',
                width: '90px',
                sort: true,
                templateRef: this.submissionCountCellTemplate(),
            },
        );

        if (this.basicPresentationEnabled()) {
            cols.push({
                headerKey: 'artemisApp.participation.presentationScore',
                field: 'presentationScore',
                width: '130px',
                sort: true,
                templateRef: this.basicPresentationCellTemplate(),
            });
        }

        if (this.gradedPresentationEnabled()) {
            cols.push({
                headerKey: 'artemisApp.participation.presentationGrade',
                field: 'presentationScore',
                width: '130px',
                sort: true,
                templateRef: this.gradedPresentationCellTemplate(),
            });
        }

        if (ex.type !== ExerciseType.QUIZ && ex.dueDate) {
            cols.push({
                headerKey: 'artemisApp.participation.individualDueDate',
                field: 'individualDueDate',
                width: '180px',
                sort: true,
                templateRef: this.individualDueDateCellTemplate(),
            });
        }

        return cols;
    });

    constructor() {
        let previouslyHidden = true;
        effect(() => {
            const hidden = this.hideParticipantInformation();
            if (hidden && !previouslyHidden) {
                untracked(() => {
                    this.participations.set([]);
                    this.loadPage();
                });
            }
            previouslyHidden = hidden;
        });
    }

    ngOnInit() {
        const scoreRangeFilter = this.route.snapshot.queryParamMap.get('scoreRangeFilter');
        if (scoreRangeFilter) {
            this.rangeFilter.set(this.scoreRanges[Number(scoreRangeFilter)]);
        }
        this.paramSub = this.route.params.subscribe((params) => {
            const course = new Course();
            course.id = Number(params['courseId']);
            this.course.set(course);
            this.loadExercise(+params['exerciseId'], !!params['examId']);
        });
        this.isAdmin.set(this.accountService.isAdmin());
        this.isLocalCIEnabled.set(this.profileService.isProfileActive(PROFILE_LOCALCI));
    }

    ngOnDestroy() {
        const ex = this.exercise();
        if (ex) {
            this.programmingSubmissionService.unsubscribeAllWebsocketTopics(ex);
        }
        this.dialogErrorSource.unsubscribe();
        this.paramSub?.unsubscribe();
        this.gradeStepsDTOSub?.unsubscribe();
        this.websocketSubscriptions.forEach((sub) => sub.unsubscribe());
    }

    private loadExercise(exerciseId: number, exam: boolean) {
        this.isLoading.set(true);
        (exam ? this.exerciseService.findForExamManagement(exerciseId) : this.exerciseService.findForStaff(exerciseId)).subscribe((exerciseResponse) => {
            const ex = exerciseResponse.body!;
            this.exercise.set(ex);
            this.newManualResultAllowed.set(areManualResultsAllowed(ex));
            this.loadGradingScale(ex.course?.id);
            if (ex.type === ExerciseType.PROGRAMMING) {
                this.loadSubmissionState(exerciseId);
                this.setupFailedFilterWebsocket(exerciseId);
            }
            this.isLoading.set(false);
        });
    }

    private loadGradingScale(courseId?: number) {
        if (courseId) {
            this.gradeStepsDTOSub = this.gradingService.findGradeStepsForCourse(courseId).subscribe((gradeStepsDTO) => {
                if (gradeStepsDTO.body) {
                    this.gradeStepsDTO.set(gradeStepsDTO.body);
                }
            });
        }
    }

    private loadSubmissionState(exerciseId: number) {
        this.programmingSubmissionService
            .getSubmissionStateOfExercise(exerciseId)
            .pipe()
            .subscribe(() => this.hasLoadedPendingSubmissions.set(true));
    }

    private setupFailedFilterWebsocket(exerciseId: number) {
        const reloadIfFailedFilterActive = () => {
            if (this.activeFilters().includes(FilterProp.FAILED)) {
                this.loadPage();
            }
        };
        const submissionSub = this.websocketService.subscribe(`/topic/exercise/${exerciseId}/newSubmissions`).subscribe(() => reloadIfFailedFilterActive());
        const resultSub = this.websocketService.subscribe(`/topic/exercise/${exerciseId}/newResults`).subscribe(() => reloadIfFailedFilterActive());
        this.websocketSubscriptions.push(submissionSub, resultSub);
    }

    onDataRequest(event: TumAetUiTableQueryEvent) {
        this.lastQuery = event;
        this.loadPage();
    }

    private loadPage() {
        const ex = this.exercise();
        if (!ex?.id || !this.lastQuery) return;

        this.isLoading.set(true);
        const requestId = ++this.currentLoadRequestId;
        const base = {
            page: this.lastQuery.pageIndex,
            pageSize: this.lastQuery.pageSize,
            searchTerm: this.lastQuery.searchTerm ?? '',
            sortedColumn: this.lastQuery.sort?.field ?? 'id',
            sortingOrder: this.lastQuery.sort?.direction === 'desc' ? SortingOrder.DESCENDING : SortingOrder.ASCENDING,
        };
        const search: ParticipationSearch = cloneWith(base, {
            searchTerm: this.hideParticipantInformation() ? '' : base.searchTerm,
            sortedColumn: this.hideParticipantInformation() && ['participantName', 'participantIdentifier', 'buildPlanId'].includes(base.sortedColumn) ? 'id' : base.sortedColumn,
            filterProp: this.activeFilters().join(',') || undefined,
            scoreRangeLower: this.rangeFilter()?.lowerBound,
            scoreRangeUpper: this.rangeFilter()?.upperBound,
        });

        this.participationService.searchParticipations(ex.id, search, ex.exerciseGroup ? 'exam' : 'course').subscribe({
            next: (result) => {
                if (requestId === this.currentLoadRequestId) {
                    this.participations.set(result.content);
                    this.totalRows.set(result.totalElements);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (requestId === this.currentLoadRequestId) {
                    this.alertService.error('artemisApp.participation.loadError');
                    this.isLoading.set(false);
                }
            },
            complete: () => {
                if (requestId === this.currentLoadRequestId) {
                    this.isLoading.set(false);
                }
            },
        });
    }

    updateParticipationFilter(newValue: string[]) {
        this.activeFilters.set(newValue.filter((value): value is FilterProp => Object.values(FilterProp).includes(value as FilterProp)));
        this.loadPage();
    }

    /**
     * Resets the score range and selected filters, then reloads data
     */
    resetFilterOptions(): void {
        this.rangeFilter.set(undefined);
        this.activeFilters.set([]);
        void this.router.navigate([], { relativeTo: this.route, queryParams: { scoreRangeFilter: undefined }, queryParamsHandling: 'merge', replaceUrl: true });
        this.loadPage();
    }

    /**
     * Triggers a re-fetch of the current page from the server
     */
    refresh() {
        this.loadPage();
    }

    getBuildPlanUrl(dto: ParticipationManagementDTO): string | undefined {
        const template = this.profileService.getProfileInfo().buildPlanURLTemplate;
        const projectKey = (this.exercise() as ProgrammingExercise).projectKey;
        if (template && projectKey && dto.buildPlanId) {
            return createBuildPlanUrl(template, projectKey, dto.buildPlanId);
        }
        return undefined;
    }

    /**
     * Exports the names of all exercise participants as a CSV file.
     */
    exportNames() {
        const ex = this.exercise();
        if (!ex?.id) return;
        this.participationService.getParticipationNamesForExport(ex.id).subscribe({
            next: (participations) => {
                if (!participations.length) return;
                const rows: string[] = [];
                participations.forEach((dto, index) => {
                    if (dto.teamStudentNames !== undefined) {
                        if (index === 0) {
                            rows.push('Team Name,Team Short Name,Students');
                        }
                        rows.push(`${dto.participantName ?? ''},${dto.participantIdentifier ?? ''},"${dto.teamStudentNames.join(', ')}"`);
                    } else {
                        rows.push(dto.participantName ?? '');
                    }
                });
                this.resultService.triggerDownloadCSV(rows, 'results-names.csv');
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Close popover for export options, since it would obstruct the newly opened modal
     */
    closeExportPopover() {
        this.exportPopover()?.close();
    }

    /**
     * Builds a Result object from the flat DTO fields for use with jhi-result.
     */
    toResult(dto: ParticipationManagementDTO): Result | undefined {
        if (!dto.resultId) return undefined;
        const result = new Result();
        result.id = dto.resultId;
        result.score = dto.score;
        result.successful = dto.successful;
        result.completionDate = dto.completionDate;
        result.assessmentType = dto.assessmentType;
        result.testCaseCount = dto.testCaseCount;
        result.passedTestCaseCount = dto.passedTestCaseCount;
        result.codeIssueCount = dto.codeIssueCount;
        result.correctionRound = dto.correctionRoundResults?.find((roundResult) => roundResult.resultId === dto.resultId)?.correctionRound;
        return result;
    }

    /**
     * Builds the results the submission row works with: the newest one, which carries the score the table shows, and
     * one per correction round, which is what the assessment actions of each round act on. The newest result is often
     * one of the rounds itself, so it is not added twice.
     */
    private toResults(dto: ParticipationManagementDTO): Result[] {
        const latestResult = this.toResult(dto);
        const roundResults = (dto.correctionRoundResults ?? [])
            .filter((roundResult) => roundResult.resultId !== dto.resultId)
            .map((roundResult) => {
                const result = new Result();
                result.id = roundResult.resultId;
                result.correctionRound = roundResult.correctionRound;
                result.assessmentType = roundResult.assessmentType;
                result.completionDate = roundResult.completionDate;
                result.hasComplaint = roundResult.hasComplaint;
                return result;
            });
        return latestResult ? [latestResult, ...roundResults] : roundResults;
    }

    /**
     * Builds a minimal student participation from the flat DTO so that jhi-result and
     * manage-assessment-buttons can render results, assessment links and cancel buttons.
     */
    toParticipation(dto: ParticipationManagementDTO): StudentParticipation {
        const ex = this.exercise();
        return {
            id: dto.participationId,
            type: ex?.type === ExerciseType.PROGRAMMING ? ParticipationType.PROGRAMMING : ParticipationType.STUDENT,
            testRun: dto.testRun,
            exercise: ex,
            submissionCount: dto.submissionCount,
            submissions: dto.submissionId ? [this.toSubmission(dto)] : [],
        };
    }

    /**
     * Builds the minimal submission that {@link toParticipation} embeds. Programming exercises get a real
     * {@link ProgrammingSubmission}, which is where `buildFailed` is declared.
     */
    private toSubmission(dto: ParticipationManagementDTO): Submission {
        const results = this.toResults(dto);
        if (this.exercise()?.type === ExerciseType.PROGRAMMING) {
            const submission = new ProgrammingSubmission();
            submission.id = dto.submissionId;
            submission.results = results;
            submission.buildFailed = dto.buildFailed;
            return submission;
        }
        return { id: dto.submissionId, results };
    }

    getParticipationLink(participationId: number): string[] {
        return this.isExamExercise() ? [participationId.toString()] : [participationId.toString(), 'submissions'];
    }

    toProgrammingParticipation(dto: ParticipationManagementDTO): ProgrammingExerciseStudentParticipation {
        const p = new ProgrammingExerciseStudentParticipation();
        p.id = dto.participationId;
        p.initializationState = dto.initializationState as InitializationState | undefined;
        p.initializationDate = dto.initializationDate;
        p.individualDueDate = dto.individualDueDate;
        p.presentationScore = dto.presentationScore;
        p.submissionCount = dto.submissionCount;
        p.participantName = dto.participantName;
        p.participantIdentifier = dto.participantIdentifier;
        p.testRun = dto.testRun;
        p.buildPlanId = dto.buildPlanId;
        p.repositoryUri = dto.repositoryUri;
        if (dto.studentId !== undefined || dto.studentLogin !== undefined) {
            const student: Partial<User> = { id: dto.studentId, login: dto.studentLogin };
            p.student = student as User;
        }
        if (dto.teamId !== undefined) {
            p.team = { id: dto.teamId };
        }
        if (dto.lastResultIsManual !== undefined) {
            const result = new Result();
            result.assessmentType = dto.lastResultIsManual ? AssessmentType.MANUAL : AssessmentType.AUTOMATIC;
            const submission: Submission = { results: [result] };
            p.submissions = [submission];
        }
        return p;
    }

    private toStudentParticipation(dto: ParticipationManagementDTO): StudentParticipation {
        const p = new StudentParticipation();
        p.id = dto.participationId;
        p.presentationScore = dto.presentationScore;
        p.individualDueDate = dto.individualDueDate;
        p.testRun = dto.testRun;
        return p;
    }

    addBasicPresentation(dto: ParticipationManagementDTO) {
        if (!this.basicPresentationEnabled()) return;
        const p = this.toStudentParticipation(dto);
        p.presentationScore = 1;
        this.participationService.update(this.exercise()!, p).subscribe({
            next: () => this.loadPage(),
            error: () => this.alertService.error('artemisApp.participation.addPresentation.error'),
        });
    }

    addGradedPresentation(dto: ParticipationManagementDTO) {
        if (!this.gradedPresentationEnabled() || (dto.presentationScore ?? 0) > 100 || (dto.presentationScore ?? 0) < 0) return;
        this.participationService.update(this.exercise()!, this.toStudentParticipation(dto)).subscribe({
            error: (res: HttpErrorResponse) => {
                const error = res.error;
                if (error?.errorKey === 'invalid.presentations.maxNumberOfPresentationsExceeded') {
                    dto.presentationScore = undefined;
                } else {
                    this.alertService.error('artemisApp.participation.savePresentation.error');
                }
            },
            complete: () => {
                this.participationsChangedPresentation.delete(dto.participationId);
                this.loadPage();
            },
        });
    }

    hasGradedPresentationChanged(dto: ParticipationManagementDTO): boolean {
        return this.participationsChangedPresentation.has(dto.participationId);
    }

    changeGradedPresentation(dto: ParticipationManagementDTO) {
        this.participationsChangedPresentation.set(dto.participationId, dto);
    }

    removePresentation(dto: ParticipationManagementDTO) {
        if (!this.basicPresentationEnabled() && !this.gradedPresentationEnabled()) return;
        const p = this.toStudentParticipation(dto);
        p.presentationScore = undefined;
        this.participationService.update(this.exercise()!, p).subscribe({
            next: () => this.loadPage(),
            error: () => this.alertService.error('artemisApp.participation.removePresentation.error'),
        });
    }

    isEditingDueDate(id: number): boolean {
        return this.editingDueDateIds().has(id);
    }

    getPendingDueDate(id: number): dayjs.Dayjs | undefined {
        return this.pendingDueDates.get(id);
    }

    setPendingDueDate(id: number, value: dayjs.Dayjs | undefined) {
        this.pendingDueDates.set(id, value);
    }

    setDueDateTextValidity(id: number, isValid: boolean) {
        this.invalidDueDateTextIds.update((ids) => {
            if (ids.has(id) === !isValid) {
                return ids;
            }
            const next = new Set(ids);
            if (isValid) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    }

    /** Whether the pending individual due date lies before the due date of the exercise. The calendar greys out earlier days, but typed text is not limited by it. */
    isBeforeExerciseDueDate(id: number): boolean {
        const pending = this.pendingDueDates.get(id);
        const dueDate = this.exercise()?.dueDate;
        return !!pending && !!dueDate && pending.isBefore(dueDate);
    }

    /** Whether the row cannot be saved: the typed text is not a date, or the date lies before the due date of the exercise. */
    isPendingDueDateInvalid(id: number): boolean {
        return this.invalidDueDateTextIds().has(id) || this.isBeforeExerciseDueDate(id);
    }

    startEditDueDate(dto: ParticipationManagementDTO) {
        this.pendingDueDates.set(dto.participationId, dto.individualDueDate);
        this.editingDueDateIds.update((s) => new Set([...s, dto.participationId]));
    }

    cancelEditDueDate(dto: ParticipationManagementDTO) {
        this.pendingDueDates.delete(dto.participationId);
        this.setDueDateTextValidity(dto.participationId, true);
        this.editingDueDateIds.update((s) => {
            const next = new Set(s);
            next.delete(dto.participationId);
            return next;
        });
    }

    saveIndividualDueDate(dto: ParticipationManagementDTO) {
        const previousDueDate = dto.individualDueDate;
        const newDueDate = this.pendingDueDates.get(dto.participationId);
        const participation = this.toStudentParticipation(dto);
        participation.individualDueDate = newDueDate;
        this.isSaving.set(true);
        this.participationService.updateIndividualDueDates(this.exercise()!, [participation]).subscribe({
            next: () => {
                dto.individualDueDate = newDueDate;
                this.pendingDueDates.delete(dto.participationId);
                this.setDueDateTextValidity(dto.participationId, true);
                this.editingDueDateIds.update((s) => {
                    const next = new Set(s);
                    next.delete(dto.participationId);
                    return next;
                });
                this.isSaving.set(false);
                this.alertService.success('artemisApp.participation.updateDueDates.success', {
                    name: dto.participantName ?? dto.participantIdentifier ?? String(dto.participationId),
                });
                this.loadPage();
            },
            error: () => {
                dto.individualDueDate = previousDueDate;
                this.pendingDueDates.delete(dto.participationId);
                this.alertService.error('artemisApp.participation.updateDueDates.error');
                this.isSaving.set(false);
            },
        });
    }

    /**
     * Deletes participation
     * @param participationId the id of the participation that we want to delete
     */
    deleteParticipation(participationId: number) {
        this.participationService.delete(participationId, { deleteBuildPlan: true, deleteRepository: true }).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.pendingDueDates.delete(participationId);
                this.setDueDateTextValidity(participationId, true);
                this.editingDueDateIds.update((s) => {
                    const next = new Set(s);
                    next.delete(participationId);
                    return next;
                });
                this.participationsChangedPresentation.delete(participationId);
                this.loadPage();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Cleans programming exercise participation
     * @param programmingExerciseParticipation the id of the participation that we want to delete
     */
    cleanupProgrammingExerciseParticipation(dto: ParticipationManagementDTO) {
        this.participationService.cleanupBuildPlan(this.toStudentParticipation(dto)).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.loadPage();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
