import { Component, OnDestroy, OnInit, TemplateRef, ViewEncapsulation, computed, inject, signal, viewChild } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Subscription, forkJoin } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { areManualResultsAllowed } from 'app/exercise/util/exercise.utils';
import { ResultService } from 'app/exercise/result/result.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { createBuildPlanUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { faComment, faDownload, faFolderOpen, faListAlt, faSync } from '@fortawesome/free-solid-svg-icons';
import { faFileCode } from '@fortawesome/free-regular-svg-icons';
import { Range } from 'app/shared/util/utils';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { FilterDropdownComponent } from 'app/exercise/shared/filter-dropdown/filter-dropdown.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExternalSubmissionButtonComponent } from '../external-submission/external-submission-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { ExerciseScoresExportButtonComponent } from './export-button/exercise-scores-export-button.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/programming/manage/assess/repo-export/export-button/programming-assessment-repo-export-button.component';
import { SubmissionExportButtonComponent } from 'app/exercise/submission-export/button/submission-export-button.component';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ManageAssessmentButtonsComponent } from './manage-assessment-buttons/manage-assessment-buttons.component';
import { ResultComponent } from '../result/result.component';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared/table-view/table-view';
import { ParticipationScoreDTO } from './participation-score-dto.model';
import { ParticipationScoreSearch } from 'app/shared/table/pageable-table';
import { TableLazyLoadEvent } from 'primeng/table';
import { buildDbQueryFromLazyEvent } from 'app/shared/table-view/request-builder';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/shared/service/alert.service';

/**
 * Filter properties for a result
 */
export enum FilterProp {
    ALL = 'All',
    SUCCESSFUL = 'Successful',
    UNSUCCESSFUL = 'Unsuccessful',
    BUILD_FAILED = 'BuildFailed',
    MANUAL = 'Manual',
    AUTOMATIC = 'Automatic',
    LOCKED = 'Locked',
}

@Component({
    selector: 'jhi-exercise-scores',
    templateUrl: './exercise-scores.component.html',
    providers: [ExerciseCacheService],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        FaIconComponent,
        RouterLink,
        ExternalSubmissionButtonComponent,
        ExerciseActionButtonComponent,
        NgbPopover,
        ExerciseScoresExportButtonComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        SubmissionExportButtonComponent,
        TableViewComponent,
        CodeButtonComponent,
        FeatureToggleLinkDirective,
        ManageAssessmentButtonsComponent,
        ResultComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
        FilterDropdownComponent,
    ],
})
export class ExerciseScoresComponent implements OnInit, OnDestroy {
    protected readonly faDownload = faDownload;
    protected readonly faSync = faSync;
    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faListAlt = faListAlt;
    protected readonly farFileCode = faFileCode;
    protected readonly faComment = faComment;
    protected readonly RepositoryType = RepositoryType;
    protected readonly ExerciseType = ExerciseType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly AssessmentType = AssessmentType;
    readonly FilterProp = FilterProp;

    private readonly route = inject(ActivatedRoute);
    private readonly courseService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly resultService = inject(ResultService);
    private readonly programmingSubmissionService = inject(ProgrammingSubmissionService);
    private readonly participationService = inject(ParticipationService);
    private readonly profileService = inject(ProfileService);
    private readonly alertService = inject(AlertService);

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

    readonly course = signal<Course | undefined>(undefined);
    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly participations = signal<ParticipationScoreDTO[]>([]);
    readonly totalRows = signal(0);
    readonly isLoading = signal(false);
    readonly afterDueDate = signal(false);
    readonly newManualResultAllowed = signal(false);
    readonly localCIEnabled = signal(true);
    readonly rangeFilter = signal<Range | undefined>(undefined);
    readonly activeFilter = signal<FilterProp>(FilterProp.ALL);
    readonly relevantFilters = computed<string[]>(() => {
        const ex = this.exercise();
        if (!ex) return [];
        return Object.values(FilterProp).filter((f) => {
            switch (f) {
                case FilterProp.BUILD_FAILED:
                    return ex.type === ExerciseType.PROGRAMMING;
                case FilterProp.MANUAL:
                case FilterProp.AUTOMATIC:
                    return this.newManualResultAllowed() || !!ex.allowComplaintsForAutomaticAssessments;
                case FilterProp.LOCKED:
                    return this.newManualResultAllowed() && !!ex.isAtLeastInstructor;
                default:
                    return true;
            }
        });
    });

    private lastLazyEvent: TableLazyLoadEvent | undefined;
    private currentLoadRequestId = 0;
    paramSub: Subscription;

    // Template refs for cell rendering
    readonly nameCellTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('nameCellTemplate');
    readonly idCellTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('idCellTemplate');
    readonly completionDateTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('completionDateTemplate');
    readonly lastResultTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('lastResultTemplate');
    readonly assessmentTypeTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('assessmentTypeTemplate');
    readonly assessmentNoteTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('assessmentNoteTemplate');
    readonly practiceTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('practiceTemplate');
    readonly submissionCountTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('submissionCountTemplate');
    readonly durationTemplate = viewChild<CellTemplateRef<ParticipationScoreDTO>>('durationTemplate');
    readonly filterActionsTemplate = viewChild<TemplateRef<unknown>>('filterDropdownTemplate');
    readonly exportPopover = viewChild<NgbPopover>('exportPopover');

    readonly tableOptions = computed<TableViewOptions>(() => ({
        striped: true,
        scrollable: true,
        scrollHeight: 'flex',
        searchPlaceholder: this.exercise()?.teamMode ? 'artemisApp.exercise.searchForTeams' : 'artemisApp.exercise.searchForStudents',
        rowActionsAlignment: 'start',
    }));

    readonly columns = computed<ColumnDef<ParticipationScoreDTO>[]>(() => {
        const ex = this.exercise();
        if (!ex) return [];

        const cols: ColumnDef<ParticipationScoreDTO>[] = [
            {
                headerKey: 'artemisApp.exercise.name',
                field: 'participantName',
                width: '180px',
                sort: true,
                templateRef: this.nameCellTemplate(),
            },
            {
                headerKey: ex.teamMode ? 'artemisApp.exercise.teamShortName' : 'artemisApp.exercise.studentId',
                field: 'participantIdentifier',
                width: '110px',
                sort: true,
                templateRef: this.idCellTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.completionDate',
                field: 'completionDate',
                width: '160px',
                sort: true,
                templateRef: this.completionDateTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.lastResult',
                field: 'score',
                width: '260px',
                sort: true,
                templateRef: this.lastResultTemplate(),
            },
        ];

        if (this.newManualResultAllowed() || ex.allowComplaintsForAutomaticAssessments) {
            cols.push({
                headerKey: 'artemisApp.exercise.type',
                field: 'assessmentType',
                width: '140px',
                sort: true,
                templateRef: this.assessmentTypeTemplate(),
            });
        }

        if (ex.assessmentType === AssessmentType.MANUAL || ex.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            cols.push({
                headerKey: 'artemisApp.assessment.assessmentNote',
                width: '100px',
                templateRef: this.assessmentNoteTemplate(),
            });
        }

        if (ex.type === ExerciseType.PROGRAMMING && this.afterDueDate()) {
            cols.push({
                headerKey: 'artemisApp.participation.practice',
                field: 'testRun',
                width: '110px',
                sort: true,
                templateRef: this.practiceTemplate(),
            });
        }

        cols.push(
            {
                headerKey: 'artemisApp.exercise.submissionCount',
                field: 'submissionCount',
                width: '110px',
                sort: true,
                templateRef: this.submissionCountTemplate(),
            },
            {
                headerKey: 'artemisApp.exercise.duration',
                width: '90px',
                templateRef: this.durationTemplate(),
            },
        );

        return cols;
    });

    ngOnInit() {
        this.localCIEnabled.set(this.profileService.isProfileActive(PROFILE_LOCALCI));
        this.paramSub = this.route.params.subscribe((params) => {
            this.isLoading.set(true);
            const filterValue = this.route.snapshot.queryParamMap.get('scoreRangeFilter');
            if (filterValue) {
                this.rangeFilter.set(this.scoreRanges[Number(filterValue)]);
            }

            forkJoin({ course: this.courseService.find(params['courseId']), exercise: this.exerciseService.find(params['exerciseId']) }).subscribe({
                next: ({ course: courseRes, exercise: exerciseRes }) => {
                    this.course.set(courseRes.body!);
                    const ex = exerciseRes.body!;
                    this.exercise.set(ex);
                    this.afterDueDate.set(!!ex.dueDate && dayjs().isAfter(ex.dueDate));
                    this.newManualResultAllowed.set(areManualResultsAllowed(ex));
                    // Initial data load will be triggered by the table's lazy load event
                    this.isLoading.set(false);
                },
                error: (error: HttpErrorResponse) => {
                    this.isLoading.set(false);
                    onError(this.alertService, error);
                },
            });
        });
    }

    onLazyLoad(event: TableLazyLoadEvent) {
        this.lastLazyEvent = event;
        this.loadPage();
    }

    private loadPage() {
        const ex = this.exercise();
        if (!ex?.id || !this.lastLazyEvent) return;

        this.isLoading.set(true);
        const requestId = ++this.currentLoadRequestId;
        const base = buildDbQueryFromLazyEvent(this.lastLazyEvent);
        const search: ParticipationScoreSearch = {
            ...base,
            filterProp: this.activeFilter() !== FilterProp.ALL ? this.activeFilter() : undefined,
            scoreRangeLower: this.rangeFilter()?.lowerBound,
            scoreRangeUpper: this.rangeFilter()?.upperBound,
        };

        this.participationService.searchParticipationScores(ex.id, search).subscribe({
            next: (result) => {
                if (requestId === this.currentLoadRequestId) {
                    this.participations.set(result.content);
                    this.totalRows.set(result.totalElements);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (requestId === this.currentLoadRequestId) {
                    this.isLoading.set(false);
                    onError(this.alertService, error);
                }
            },
            complete: () => {
                if (requestId === this.currentLoadRequestId) {
                    this.isLoading.set(false);
                }
            },
        });
    }

    getExerciseParticipationsLink(participationId: number): string[] {
        const ex = this.exercise()!;
        const course = this.course()!;
        return ex.exerciseGroup
            ? [
                  '/course-management',
                  course.id!.toString(),
                  'exams',
                  ex.exerciseGroup!.exam!.id!.toString(),
                  'exercise-groups',
                  ex.exerciseGroup!.id!.toString(),
                  ex.type + '-exercises',
                  ex.id!.toString(),
                  'participations',
                  participationId.toString(),
              ]
            : ['/course-management', course.id!.toString(), ex.type + '-exercises', ex.id!.toString(), 'participations', participationId.toString(), 'submissions'];
    }

    updateParticipationFilter(newValue: string) {
        this.activeFilter.set(newValue as FilterProp);
        this.loadPage();
    }

    getBuildPlanUrl(dto: ParticipationScoreDTO): string | undefined {
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
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Triggers a re-fetch of the current page from the server
     */
    refresh() {
        this.loadPage();
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        const ex = this.exercise();
        if (ex) {
            this.programmingSubmissionService.unsubscribeAllWebsocketTopics(ex);
        }
    }

    /**
     * Resets the score range filter and active filter, then reloads data
     */
    resetFilterOptions(): void {
        this.rangeFilter.set(undefined);
        this.activeFilter.set(FilterProp.ALL);
        this.loadPage();
    }

    /**
     * Builds a Result object from the flat DTO fields for use with jhi-result.
     */
    toResult(dto: ParticipationScoreDTO): Result | undefined {
        if (!dto.resultId) return undefined;
        const result = new Result();
        result.id = dto.resultId;
        result.score = dto.score;
        result.successful = dto.successful;
        result.completionDate = dto.completionDate;
        result.assessmentType = dto.assessmentType;
        return result;
    }

    /**
     * Builds a minimal Participation-like object from the flat DTO so that
     * manage-assessment-buttons can render assessment links and cancel buttons.
     */
    toParticipation(dto: ParticipationScoreDTO): Participation {
        const ex = this.exercise();
        return {
            id: dto.participationId,
            type: ex?.type === ExerciseType.PROGRAMMING ? ParticipationType.PROGRAMMING : ParticipationType.STUDENT,
            exercise: ex,
            submissionCount: dto.submissionCount,
            submissions: dto.submissionId
                ? [
                      {
                          id: dto.submissionId,
                          results: dto.resultId
                              ? [
                                    {
                                        id: dto.resultId,
                                        score: dto.score,
                                        successful: dto.successful,
                                        completionDate: dto.completionDate,
                                        assessmentType: dto.assessmentType,
                                    },
                                ]
                              : [],
                      },
                  ]
                : [],
        } as Participation;
    }

    /**
     * Close popover for export options, since it would obstruct the newly opened modal
     */
    closeExportPopover() {
        this.exportPopover()?.close();
    }
}
