import { Component, DestroyRef, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, merge } from 'rxjs';
import { debounceTime, finalize, map, tap } from 'rxjs/operators';

import { faArrowUpRightFromSquare, faLink, faPencilAlt, faPlus, faSearch, faTrash, faUsers } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiButtonGroupComponent,
    TumAetUiDialogComponent,
    TumAetUiIconFieldComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiPaginatorComponent,
    TumAetUiSelectComponent,
    TumAetUiTableDirective,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
    TumAetUiTagComponent,
} from '@tumaet/ui-angular';

import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { isErrorAlert, onError } from 'app/foundation/util/global.utils';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import {
    PresentationAssessment,
    PresentationAssessmentInstance,
    PresentationAssessmentMode,
    PresentationAssessmentStatistics,
    PresentationAssessmentStudentRow,
    PresentationAssessmentStudentRowsRequest,
} from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { PresentationAssessmentFormDialogComponent, PresentationAssessmentFormDialogResult } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { ExerciseTitle } from 'app/exercise/shared/entities/exercise/exercise-title.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import {
    PresentationAssessmentInstanceFormDialogComponent,
    PresentationAssessmentInstanceFormResult,
} from 'app/presentation/manage/presentation-assessment-instance-form-dialog.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { CollapseState, SidebarItemShowAlways } from 'app/foundation/types/sidebar';
import { TranslateService } from '@ngx-translate/core';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import {
    AssessmentStatusFilter,
    FilterOption,
    PresentationStudentRow,
    PresentationTypeFilter,
    PresentationViewMode,
    SelectedPresentationStudentRow,
    createPresentationSidebarData,
    hasResultPoints,
} from 'app/presentation/manage/presentation-assessment-management.helper';

const presentationSidebarCollapseStateRecord: Record<string, boolean> = { standalone: false, linkedToExercise: false };
const PRESENTATION_SIDEBAR_COLLAPSE_STATE = presentationSidebarCollapseStateRecord as CollapseState;
const presentationSidebarAlwaysShowRecord: Record<string, boolean> = {};
const PRESENTATION_SIDEBAR_ALWAYS_SHOW = presentationSidebarAlwaysShowRecord as SidebarItemShowAlways;
/** Time without typing before the search term is sent to the server. */
const STUDENT_SEARCH_DEBOUNCE_MS = 300;

function studentRowKey(row: PresentationStudentRow | SelectedPresentationStudentRow): string {
    return `${row.instance?.id ?? 'new'}:${row.studentLogin}`;
}

function decorateStudentRow(row: PresentationStudentRow | SelectedPresentationStudentRow, expandedRows: string[]) {
    const rowKey = studentRowKey(row);
    return {
        studentLogin: row.studentLogin,
        student: row.student,
        presentationAssessment: row.presentationAssessment,
        instance: row.instance,
        rowKey,
        assessed: hasResultPoints(row.instance?.resultPoints),
        expanded: expandedRows.includes(rowKey),
    };
}

function positiveRouteId(value: string | null): number | undefined {
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

@Component({
    selector: 'jhi-presentation-assessment-management',
    templateUrl: './presentation-assessment-management.component.html',
    styleUrl: './presentation-assessment-management.component.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        ArtemisDatePipe,
        CourseTitleBarActionsDirective,
        FormsModule,
        ArtemisTranslatePipe,
        RouterLink,
        PresentationAssessmentFormDialogComponent,
        PresentationAssessmentInstanceFormDialogComponent,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiButtonGroupComponent,
        TumAetUiDialogComponent,
        TumAetUiIconFieldComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiPaginatorComponent,
        TumAetUiSelectComponent,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TumAetUiTagComponent,
        SidebarComponent,
        DeleteButtonDirective,
    ],
})
export class PresentationAssessmentManagementComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly presentationAssessmentService = inject(PresentationAssessmentService);
    private readonly alertService = inject(AlertService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly translationChanges = toSignal(merge(this.translateService.onLangChange, this.translateService.onTranslationChange));
    private readonly routeSelection = toSignal(
        this.route.paramMap.pipe(
            map((params) => ({
                courseId: Number(params.get('courseId') ?? this.route.parent?.snapshot.paramMap.get('courseId')),
                presentationId: positiveRouteId(params.get('presentationId')),
                exerciseId: positiveRouteId(params.get('exerciseId')),
            })),
        ),
        { initialValue: { courseId: 0, presentationId: undefined, exerciseId: undefined } },
    );

    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faLink = faLink;
    protected readonly faUsers = faUsers;
    protected readonly faSearch = faSearch;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    protected readonly PresentationAssessmentMode = PresentationAssessmentMode;
    protected readonly ActionType = ActionType;

    private courseContextGeneration = 0;

    readonly courseId = signal<number>(0);
    readonly course = signal<Course | undefined>(undefined);
    readonly presentationAssessments = signal<PresentationAssessment[]>([]);
    private readonly presentationAssessmentsLoaded = signal(false);
    readonly presentationLoadFailed = signal(false);
    readonly isSaving = signal(false);
    readonly exercises = signal<ExerciseTitle[]>([]);
    readonly viewMode = signal<PresentationViewMode>('students');
    readonly selectedPresentationId = signal<number | undefined>(undefined);
    readonly contentReady = computed(() => {
        const selection = this.routeSelection();
        return (
            this.presentationAssessmentsLoaded() &&
            selection.courseId === this.courseId() &&
            (selection.presentationId === undefined
                ? this.viewMode() === 'students'
                : this.viewMode() === 'presentations' && this.selectedPresentationId() === selection.presentationId)
        );
    });
    readonly studentSearchTerm = signal('');
    readonly assessmentStatusFilter = signal<AssessmentStatusFilter>('all');
    readonly presentationFilter = signal<number | 'all'>('all');
    readonly presentationTypeFilter = signal<PresentationTypeFilter>('all');
    readonly overviewPage = signal(0);
    readonly overviewPageSize = signal(25);
    readonly expandedStudentRows = signal<string[]>([]);
    readonly presentationDialogVisible = signal(false);
    readonly dialogPresentationAssessment = signal<PresentationAssessment | undefined>(undefined);
    readonly instanceDialogVisible = signal(false);
    readonly dialogInstancePresentationAssessment = signal<PresentationAssessment | undefined>(undefined);
    readonly dialogInstance = signal<PresentationAssessmentInstance | undefined>(undefined);
    readonly dialogAssignedStudents = signal<User[]>([]);
    readonly studentSortField = signal<PresentationAssessmentStudentRowsRequest['sortField']>('studentLogin');
    readonly studentSortOrder = signal(1);
    readonly studentRowsRequest = computed<PresentationAssessmentStudentRowsRequest>(() => {
        const isOverview = this.viewMode() === 'students';
        const presentationFilter = this.presentationFilter();
        const status = this.assessmentStatusFilter();
        const type = this.presentationTypeFilter();

        return {
            page: this.overviewPage(),
            size: this.overviewPageSize(),
            sortField: this.studentSortField(),
            direction: this.studentSortOrder() === -1 ? 'DESC' : 'ASC',
            assessmentId: isOverview ? (presentationFilter === 'all' ? undefined : presentationFilter) : this.selectedPresentationId(),
            assessed: isOverview && status !== 'all' ? status === 'assessed' : undefined,
            linkedToExercise: isOverview && type !== 'all' ? type === 'exercise' : undefined,
            searchTerm: this.studentSearchTerm().trim() || undefined,
        };
    });
    private readonly studentRowsResource = rxResource({
        params: () => {
            const courseId = this.courseId();
            return courseId && this.contentReady() ? { courseId, request: this.studentRowsRequest() } : undefined;
        },
        stream: ({ params }) =>
            this.presentationAssessmentService.findStudentRows(params.courseId, params.request).pipe(
                tap({
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                }),
            ),
    });
    private readonly statisticsResource = rxResource({
        params: () => (this.viewMode() === 'students' && this.contentReady() ? this.courseId() || undefined : undefined),
        stream: ({ params: courseId }) =>
            this.presentationAssessmentService.getStatistics(courseId).pipe(
                tap({
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                }),
            ),
    });
    readonly statistics = computed<PresentationAssessmentStatistics | undefined>(() =>
        this.statisticsResource.hasValue() ? (this.statisticsResource.value().body ?? undefined) : undefined,
    );
    readonly loadedStudentRows = computed<PresentationAssessmentStudentRow[]>(() => (this.studentRowsResource.hasValue() ? (this.studentRowsResource.value().body ?? []) : []));
    readonly totalStudentRows = computed(() => (this.studentRowsResource.hasValue() ? Number(this.studentRowsResource.value().headers.get('X-Total-Count') ?? 0) : 0));
    readonly isLoadingStudentRows = this.studentRowsResource.isLoading;
    readonly studentRowsLoadFailed = computed(() => this.studentRowsResource.error() !== undefined);
    readonly sidebarCollapseState = PRESENTATION_SIDEBAR_COLLAPSE_STATE;
    readonly sidebarItemAlwaysShow = PRESENTATION_SIDEBAR_ALWAYS_SHOW;
    readonly selectedPresentation = computed(() => {
        const selectedId = this.selectedPresentationId();
        return this.presentationAssessments().find((assessment) => assessment.id === selectedId) ?? this.presentationAssessments()[0];
    });
    readonly selectedPresentationExerciseRoute = computed<(string | number)[] | undefined>(() => {
        const presentationAssessment = this.selectedPresentation();
        const exercise = this.exercises().find((candidate) => candidate.id === presentationAssessment?.exerciseId);
        return exercise?.id && exercise.type ? ['/course-management', this.courseId(), `${exercise.type}-exercises`, exercise.id] : undefined;
    });
    readonly currentPageStudentRows = computed<PresentationStudentRow[]>(() =>
        this.loadedStudentRows().flatMap(({ presentationAssessment, instance }): PresentationStudentRow[] => {
            const student = instance.student;
            if (!student?.login) {
                return [];
            }

            return [
                {
                    studentLogin: student.login,
                    student: hydrate(new User(), student),
                    presentationAssessment,
                    instance,
                },
            ];
        }),
    );
    readonly studentRows = computed(() => {
        const expandedRows = this.expandedStudentRows();
        return this.currentPageStudentRows().map((row) => decorateStudentRow(row, expandedRows));
    });
    readonly assessedStudentCount = computed(() => this.statistics()?.assessedCount);
    readonly pendingStudentCount = computed(() => {
        const statistics = this.statistics();
        return statistics ? statistics.totalCount - statistics.assessedCount : undefined;
    });
    readonly presentationFilterOptions = computed<FilterOption<number | 'all'>[]>(() => {
        this.translationChanges();
        return [
            { label: this.translateService.instant('artemisApp.presentationAssessment.filter.allPresentations'), value: 'all' },
            ...this.presentationAssessments().map((assessment) => ({ label: assessment.title ?? '-', value: assessment.id! })),
        ];
    });
    readonly typeFilterOptions = computed<FilterOption<PresentationTypeFilter>[]>(() => {
        this.translationChanges();
        return [
            { label: this.translateService.instant('artemisApp.presentationAssessment.filter.allTypes'), value: 'all' },
            { label: this.translateService.instant('artemisApp.presentationAssessment.standalone'), value: 'standalone' },
            { label: this.translateService.instant('artemisApp.presentationAssessment.linkedToExercise'), value: 'exercise' },
        ];
    });
    readonly sidebarData = computed(() => {
        this.translationChanges();
        return createPresentationSidebarData(this.presentationAssessments(), this.courseId(), (key) => this.translateService.instant(key), this.faUsers, this.faLink);
    });
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    private readonly studentSearchInput = new Subject<{ searchTerm: string; generation: number }>();

    constructor() {
        // The term is only committed (and sent to the server) once typing pauses; a new page starts with the new term.
        // A term typed in a previous course is dropped, like every other stale callback of this component.
        this.studentSearchInput.pipe(debounceTime(STUDENT_SEARCH_DEBOUNCE_MS), takeUntilDestroyed()).subscribe(({ searchTerm, generation }) => {
            if (generation === this.courseContextGeneration) {
                this.studentSearchTerm.set(searchTerm);
                this.overviewPage.set(0);
            }
        });
        effect(() => {
            const { courseId, presentationId, exerciseId } = this.routeSelection();
            const assessments = this.presentationAssessments();
            const assessmentsLoaded = this.presentationAssessmentsLoaded();
            untracked(() => {
                if (courseId && courseId !== this.courseId()) {
                    this.courseContextGeneration++;
                    this.isSaving.set(false);
                    this.courseId.set(courseId);
                    this.presentationAssessmentsLoaded.set(false);
                    this.presentationLoadFailed.set(false);
                    this.presentationDialogVisible.set(false);
                    this.instanceDialogVisible.set(false);
                    this.dialogPresentationAssessment.set(undefined);
                    this.dialogInstancePresentationAssessment.set(undefined);
                    this.dialogInstance.set(undefined);
                    this.dialogAssignedStudents.set([]);
                    this.dialogErrorSource.next('');
                    this.presentationAssessments.set([]);
                    this.selectedPresentationId.set(undefined);
                    this.exercises.set([]);
                    this.expandedStudentRows.set([]);
                    this.overviewPage.set(0);
                    this.presentationFilter.set('all');
                    this.studentSearchTerm.set('');
                    this.assessmentStatusFilter.set('all');
                    this.presentationTypeFilter.set('all');
                    this.studentSortField.set('studentLogin');
                    this.studentSortOrder.set(1);
                    this.loadAll();
                    this.exerciseService.getTitlesForCourse(courseId).subscribe({
                        next: (exercises) => {
                            if (this.courseId() === courseId) {
                                this.exercises.set(exercises);
                            }
                        },
                        error: (res: HttpErrorResponse) => onError(this.alertService, res),
                    });
                    return;
                }
                if (presentationId === undefined) {
                    if (this.viewMode() !== 'students') {
                        this.overviewPage.set(0);
                    }
                    this.viewMode.set('students');
                    return;
                }

                const presentation = assessments.find((assessment) => assessment.id === presentationId);
                if (!presentation && assessmentsLoaded) {
                    void this.router.navigate(['/course-management', this.courseId(), 'presentations'], { replaceUrl: true });
                    return;
                }
                if (!presentation) {
                    return;
                }

                if (this.viewMode() !== 'presentations' || this.selectedPresentationId() !== presentation.id) {
                    this.overviewPage.set(0);
                }
                this.selectedPresentationId.set(presentation.id);
                this.viewMode.set('presentations');
                if (presentation.exerciseId !== exerciseId) {
                    this.navigateToPresentation(presentation, true);
                }
            });
        });
    }

    ngOnInit(): void {
        this.route.parent?.data.subscribe(({ course }) => this.course.set(course));
    }

    retryLoading(): void {
        if (this.presentationLoadFailed()) {
            this.loadAll();
        } else {
            this.studentRowsResource.reload();
        }
    }

    loadAll(): void {
        const courseId = this.courseId();
        this.presentationLoadFailed.set(false);
        this.presentationAssessmentService.findAllByCourseId(courseId).subscribe({
            next: (res: HttpResponse<PresentationAssessment[]>) => {
                if (this.courseId() !== courseId) {
                    return;
                }
                const assessments = res.body ?? [];
                this.presentationAssessments.set(assessments);
                this.presentationAssessmentsLoaded.set(true);
                if (!assessments.some((assessment) => assessment.id === this.selectedPresentationId())) {
                    this.selectedPresentationId.set(assessments[0]?.id);
                }
            },
            error: (res: HttpErrorResponse) => {
                if (this.courseId() !== courseId) {
                    return;
                }
                this.presentationLoadFailed.set(true);
                onError(this.alertService, res);
            },
        });
    }

    startCreate(): void {
        this.openPresentationDialog();
    }

    selectPresentation(presentationAssessment: PresentationAssessment): void {
        this.overviewPage.set(0);
        this.selectedPresentationId.set(presentationAssessment.id);
        this.viewMode.set('presentations');
        this.navigateToPresentation(presentationAssessment);
    }

    setViewMode(viewMode: PresentationViewMode): void {
        if (this.viewMode() !== viewMode) {
            this.overviewPage.set(0);
        }
        this.viewMode.set(viewMode);
        if (viewMode === 'students') {
            void this.router.navigate(['/course-management', this.courseId(), 'presentations']);
        }
    }

    updateStudentSearch(searchTerm: string): void {
        this.studentSearchInput.next({ searchTerm, generation: this.courseContextGeneration });
    }

    onStudentSort(event: TumAetUiTableSortEvent): void {
        const field = event.field;
        if (field !== 'studentLogin' && field !== 'presentationTitle' && field !== 'presentationDate' && field !== 'resultPoints') {
            return;
        }
        this.studentSortField.set(field);
        this.studentSortOrder.set(event.order);
        this.overviewPage.set(0);
    }

    setAssessmentStatusFilter(filter: AssessmentStatusFilter): void {
        this.assessmentStatusFilter.set(filter);
        this.overviewPage.set(0);
    }

    updatePresentationFilter(filter: number | 'all'): void {
        this.presentationFilter.set(filter);
        this.overviewPage.set(0);
    }

    updatePresentationTypeFilter(filter: PresentationTypeFilter): void {
        this.presentationTypeFilter.set(filter);
        this.overviewPage.set(0);
    }

    updateOverviewPageSize(pageSize: number): void {
        this.overviewPageSize.set(pageSize);
        this.overviewPage.set(0);
    }

    toggleStudentRowDetails(row: PresentationStudentRow | SelectedPresentationStudentRow): void {
        if (!row.instance) {
            return;
        }
        const key = studentRowKey(row);
        this.expandedStudentRows.update((keys) => (keys.includes(key) ? keys.filter((value) => value !== key) : [...keys, key]));
    }

    startEdit(presentationAssessment: PresentationAssessment): void {
        this.openPresentationDialog(presentationAssessment);
    }

    startCreateInstance(presentationAssessment: PresentationAssessment): void {
        this.openInstanceDialog(presentationAssessment);
    }

    startEditInstance(presentationAssessment: PresentationAssessment, instance: PresentationAssessmentInstance): void {
        this.openInstanceDialog(presentationAssessment, instance);
    }

    deleteInstance(presentationAssessment: PresentationAssessment, instance: PresentationAssessmentInstance): void {
        if (!presentationAssessment.id || !instance.id) {
            return;
        }

        const courseId = this.courseId();
        const generation = this.courseContextGeneration;

        this.presentationAssessmentService.deleteInstance(courseId, presentationAssessment.id, instance.id).subscribe({
            next: () => {
                if (generation !== this.courseContextGeneration) {
                    return;
                }

                if (this.loadedStudentRows().length === 1 && this.overviewPage() > 0) {
                    this.overviewPage.update((page) => page - 1);
                } else {
                    this.studentRowsResource.reload();
                }
                this.statisticsResource.reload();
            },
            error: (res: HttpErrorResponse) => {
                if (generation === this.courseContextGeneration) {
                    this.onWriteError(res);
                }
            },
        });
    }

    deletePresentationAssessment(presentationAssessment: PresentationAssessment): void {
        if (!presentationAssessment.id) {
            return;
        }

        const courseId = this.courseId();
        const generation = this.courseContextGeneration;

        this.isSaving.set(true);
        this.presentationAssessmentService
            .delete(courseId, presentationAssessment.id)
            .pipe(
                // A response that arrives after the user left the page must not navigate them back.
                takeUntilDestroyed(this.destroyRef),
                finalize(() => {
                    if (generation === this.courseContextGeneration) {
                        this.isSaving.set(false);
                    }
                }),
            )
            .subscribe({
                next: () => {
                    if (generation !== this.courseContextGeneration) {
                        return;
                    }

                    this.dialogErrorSource.next('');
                    this.presentationDialogVisible.set(false);
                    const remainingAssessments = this.presentationAssessments().filter((assessment) => assessment.id !== presentationAssessment.id);
                    this.presentationAssessments.set(remainingAssessments);
                    if (this.selectedPresentationId() === presentationAssessment.id) {
                        // The route still names the deleted presentation until the navigation completes, and the route handling
                        // sends such a route to the overview. Going there directly keeps the outcome deterministic.
                        this.selectedPresentationId.set(remainingAssessments[0]?.id);
                        this.setViewMode('students');
                    }
                    if (this.presentationFilter() === presentationAssessment.id) {
                        this.presentationFilter.set('all');
                    }
                    this.overviewPage.set(0);
                    this.studentRowsResource.reload();
                    this.statisticsResource.reload();
                    this.alertService.success('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
                },
                error: (error: HttpErrorResponse) => {
                    if (generation === this.courseContextGeneration) {
                        this.dialogErrorSource.next(error.message);
                    }
                },
            });
    }

    /** The server already shows its own message for errors that carry an error key (for example a 409 conflict), so no generic alert is added. */
    private onWriteError(error: HttpErrorResponse): void {
        if (!isErrorAlert(error)) {
            onError(this.alertService, error);
        }
    }

    private navigateToPresentation(presentationAssessment: PresentationAssessment, replaceUrl = false): void {
        if (!presentationAssessment.id) {
            return;
        }
        const commands: (string | number)[] = ['/course-management', this.courseId(), 'presentations', presentationAssessment.id];
        if (presentationAssessment.exerciseId) {
            commands.push('exercises', presentationAssessment.exerciseId);
        }
        void this.router.navigate(commands, { replaceUrl });
    }

    private openPresentationDialog(presentationAssessment?: PresentationAssessment): void {
        this.dialogPresentationAssessment.set(presentationAssessment);
        this.presentationDialogVisible.set(true);
    }

    handlePresentationDialogSave(result: PresentationAssessmentFormDialogResult): void {
        const courseId = this.courseId();
        const generation = this.courseContextGeneration;

        this.isSaving.set(true);
        const presentationAssessment = result.presentationAssessment;
        const isUpdate = Boolean(presentationAssessment.id);
        const request: Observable<HttpResponse<PresentationAssessment>> = isUpdate
            ? this.presentationAssessmentService.update(courseId, presentationAssessment)
            : this.presentationAssessmentService.create(courseId, presentationAssessment);

        request
            .pipe(
                finalize(() => {
                    if (generation === this.courseContextGeneration) {
                        this.isSaving.set(false);
                    }
                }),
            )
            .subscribe({
                next: (response) => {
                    if (generation !== this.courseContextGeneration) {
                        return;
                    }

                    this.presentationDialogVisible.set(false);
                    const savedAssessment = response.body;
                    if (savedAssessment) {
                        this.presentationAssessments.update((assessments) => [...assessments.filter((assessment) => assessment.id !== savedAssessment.id), savedAssessment]);
                        if (this.selectedPresentationId() === undefined) {
                            this.selectedPresentationId.set(savedAssessment.id);
                        }
                    } else {
                        // Saving succeeded, but the response body is missing. Reload the list to recover the saved state.
                        this.loadAll();
                    }
                    if (isUpdate) {
                        this.studentRowsResource.reload();
                    }
                    this.alertService.success(isUpdate ? 'artemisApp.presentationAssessment.updated' : 'artemisApp.presentationAssessment.created');
                },
                error: (res: HttpErrorResponse) => {
                    if (generation === this.courseContextGeneration) {
                        this.onWriteError(res);
                    }
                },
            });
    }

    handlePresentationDialogCancel(): void {
        if (this.isSaving()) {
            return;
        }
        this.presentationDialogVisible.set(false);
    }

    handlePresentationDialogDelete(presentationAssessment: PresentationAssessment): void {
        if (this.isSaving()) {
            return;
        }
        this.deletePresentationAssessment(presentationAssessment);
    }

    private openInstanceDialog(presentationAssessment: PresentationAssessment, instance?: PresentationAssessmentInstance): void {
        const course = this.course();
        if (!presentationAssessment.id || !course) {
            return;
        }
        this.dialogInstancePresentationAssessment.set(presentationAssessment);
        this.dialogInstance.set(instance);
        this.dialogAssignedStudents.set(instance?.student ? [hydrate(new User(), instance.student)] : []);
        this.instanceDialogVisible.set(true);
    }

    handleInstanceDialogSave(result: PresentationAssessmentInstanceFormResult): void {
        const presentationAssessment = this.dialogInstancePresentationAssessment();
        if (!presentationAssessment?.id || this.isSaving()) {
            return;
        }

        const courseId = this.courseId();
        const generation = this.courseContextGeneration;
        const request: Observable<unknown> =
            result.kind === 'create'
                ? this.presentationAssessmentService.saveInstances(courseId, presentationAssessment.id, result.request)
                : this.presentationAssessmentService.updateInstance(courseId, presentationAssessment.id, result.instance);

        this.isSaving.set(true);
        request
            .pipe(
                finalize(() => {
                    if (generation === this.courseContextGeneration) {
                        this.isSaving.set(false);
                    }
                }),
            )
            .subscribe({
                next: () => {
                    if (generation !== this.courseContextGeneration) {
                        return;
                    }

                    this.instanceDialogVisible.set(false);
                    this.studentRowsResource.reload();
                    this.statisticsResource.reload();
                },
                error: (res: HttpErrorResponse) => {
                    if (generation === this.courseContextGeneration) {
                        this.onWriteError(res);
                    }
                },
            });
    }

    handleInstanceDialogCancel(): void {
        if (this.isSaving()) {
            return;
        }
        this.instanceDialogVisible.set(false);
    }
}
