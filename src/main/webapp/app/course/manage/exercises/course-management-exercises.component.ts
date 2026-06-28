import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { QUIZ_EXPORT_BACK, QuizExerciseExportComponent } from 'app/quiz/manage/export/quiz-exercise-export.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';
import { PanelModule } from 'primeng/panel';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCalendarDays, faCircleInfo, faCode, faFileExport, faFileImport, faLayerGroup, faList, faPen, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, ExerciseVariantGroupReference, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseExerciseGroup, effectiveDate } from 'app/core/course/manage/exercises/course-exercise-group.model';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService, toCourseExerciseGroup } from 'app/core/course/manage/exercises/exercise-variant-group.service';
import { ExerciseTableComponent, TableGroupChange } from 'app/core/course/manage/exercises-experimental/exercise-row/exercise-table.component';
import { AddModalMode, ExerciseAddModalComponent } from 'app/core/course/manage/exercises-experimental/create-modal/exercise-add-modal.component';
import { ExerciseGroupEditModalComponent } from 'app/core/course/manage/exercises-experimental/group-edit-modal/exercise-group-edit-modal.component';
import { DialogTranslateHeaderComponent } from 'app/shared-ui/dynamic-dialog/dialog-translate-header.component';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarToolbarDirective } from 'app/course/shared/directives/course-title-bar-toolbar.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

type View = 'type' | 'week' | 'group' | 'list';

/** Local-storage key under which the last-selected view is remembered, so closing an exercise editor returns to it. */
const VIEW_STORAGE_KEY = 'artemis.exerciseManagement.view';

interface Bucket {
    id: string;
    title: string;
    icon?: IconProp;
    group?: CourseExerciseGroup;
    exerciseType?: ExerciseType;
    exercises: Exercise[];
}

const TYPE_ORDER: ExerciseType[] = [ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];
const TYPE_TITLE_KEYS: Record<string, string> = {
    [ExerciseType.PROGRAMMING]: 'artemisApp.exerciseManagement.type.PROGRAMMING',
    [ExerciseType.QUIZ]: 'artemisApp.exerciseManagement.type.QUIZ',
    [ExerciseType.MODELING]: 'artemisApp.exerciseManagement.type.MODELING',
    [ExerciseType.TEXT]: 'artemisApp.exerciseManagement.type.TEXT',
    [ExerciseType.FILE_UPLOAD]: 'artemisApp.exerciseManagement.type.FILE_UPLOAD',
};

@Component({
    selector: 'jhi-course-management-exercises',
    templateUrl: './course-management-exercises.component.html',
    styleUrl: './course-management-exercises.component.scss',
    imports: [
        FormsModule,
        SelectButtonModule,
        PanelModule,
        ButtonModule,
        TooltipModule,
        FaIconComponent,
        ExerciseTableComponent,
        ExerciseAddModalComponent,
        SearchFilterComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        CourseTitleBarTitleDirective,
        CourseTitleBarToolbarDirective,
        CourseTitleBarActionsDirective,
        TranslateDirective,
    ],
})
export class CourseManagementExercisesComponent implements OnInit {
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;
    protected readonly faFileExport = faFileExport;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faPen = faPen;
    protected readonly faTrash = faTrash;
    protected readonly faLayerGroup = faLayerGroup;
    protected readonly ExerciseType = ExerciseType;

    readonly viewOptions: { labelKey: string; value: View; icon: IconProp }[] = [
        { labelKey: 'artemisApp.exerciseManagement.view.list', value: 'list', icon: faList },
        { labelKey: 'artemisApp.exerciseManagement.view.type', value: 'type', icon: faCode },
        { labelKey: 'artemisApp.exerciseManagement.view.week', value: 'week', icon: faCalendarDays },
        { labelKey: 'artemisApp.exerciseManagement.view.group', value: 'group', icon: faLayerGroup },
    ];

    readonly view = signal<View>('type');
    readonly search = signal('');

    readonly course = signal<Course | undefined>(undefined);
    readonly exercises = signal<Exercise[]>([]);
    readonly groups = signal<CourseExerciseGroup[]>([]);
    readonly buckets = signal<Bucket[]>([]);
    /**
     * Whether the initial exercise load has finished. Gates the "no exercises match" empty state so it is not shown
     * during the brief window before the exercises arrive on first (direct) access — switching views afterwards keeps
     * this true, so a genuinely empty result (e.g. a search with no matches) still shows the message.
     */
    readonly loaded = signal(false);

    readonly selectedIds = signal<Set<number>>(new Set());
    readonly addModalVisible = signal(false);
    readonly addModalMode = signal<AddModalMode>('create');

    readonly isGroup = computed(() => this.view() === 'group');
    /** Deleting a group requires the same permission as deleting an exercise: instructor (or admin) on the course. */
    readonly canDeleteGroups = computed(() => this.course()?.isAtLeastInstructor ?? false);
    /** Ids of all rendered buckets, so each group's exercise table is a connected CDK drop target for the others. */
    readonly dropListIds = computed(() => this.buckets().map((bucket) => bucket.id));
    readonly exerciseCount = computed(() => this.exercises().length);
    readonly courseId = computed(() => this.course()?.id);
    readonly selectedCount = computed(() => this.selectedIds().size);

    readonly selectedTypes = computed(() => {
        const ids = this.selectedIds();
        return new Set(
            this.exercises()
                .filter((e) => e.id !== undefined && ids.has(e.id!))
                .map((e) => e.type),
        );
    });
    readonly allSelectedAreProgramming = computed(() => {
        const t = this.selectedTypes();
        return t.size === 1 && t.has(ExerciseType.PROGRAMMING);
    });

    private readonly route = inject(ActivatedRoute);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);
    private readonly exerciseVariantGroupService = inject(ExerciseVariantGroupService);
    private readonly deleteDialogService = inject(DeleteDialogService);
    private readonly alertService = inject(AlertService);
    private readonly localStorageService = inject(LocalStorageService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly groupDeleteError = new Subject<string>();

    constructor() {
        // Restore the last-selected view so editing an exercise (which navigates away and re-instantiates this
        // component on return) keeps the chosen view instead of falling back to the 'type' default. The stored value
        // is validated against the known views so a stale or corrupt entry simply falls back to the default.
        const storedView = this.localStorageService.retrieve<View>(VIEW_STORAGE_KEY);
        if (storedView && this.viewOptions.some((option) => option.value === storedView)) {
            this.view.set(storedView);
        }
    }

    ngOnInit(): void {
        // Bucket titles are resolved eagerly via TranslateService.instant (the view view-mode and type labels), so in
        // this zoneless app they must be rebuilt when the language changes — otherwise they keep the previous language
        // until the next user interaction rebuilds the buckets.
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.buildBuckets());

        this.route.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
            }
            if (course?.id) {
                const courseId = course.id;
                this.courseManagementService.findWithExercises(courseId).subscribe({
                    next: (response) => {
                        const loadedCourse = response.body;
                        const exercises = loadedCourse?.exercises ?? [];
                        exercises.forEach((exercise) => {
                            exercise.isAtLeastTutor = loadedCourse?.isAtLeastTutor;
                            exercise.isAtLeastEditor = loadedCourse?.isAtLeastEditor;
                            exercise.isAtLeastInstructor = loadedCourse?.isAtLeastInstructor;
                            if (exercise.type === ExerciseType.QUIZ) {
                                this.applyQuizClientState(exercise as QuizExercise);
                            }
                        });
                        this.exercises.set(exercises);
                        this.loadGroupsFromServer(courseId);
                        this.buildBuckets();
                        this.loaded.set(true);
                        this.loadQuizBatches(courseId);
                    },
                });
            } else {
                this.buildBuckets();
                this.loaded.set(true);
            }
        });
    }

    onViewChange(view: View): void {
        this.view.set(view);
        // Remember the selection so it is restored when the component is re-instantiated (e.g. after closing an editor).
        this.localStorageService.store(VIEW_STORAGE_KEY, view);
        this.buildBuckets();
    }

    onSearchChange(term: string): void {
        this.search.set(term);
        this.buildBuckets();
    }

    owningGroup(exercise: Exercise): CourseExerciseGroup | undefined {
        return this.groups().find((group) => group.exercises?.some((member) => member.id === exercise.id));
    }

    /** Only individual-mode quizzes support per-student dates, so only they can join a group's shared timeline. */
    private isQuizNonIndividual(exercise: Exercise): boolean {
        return exercise.type === ExerciseType.QUIZ && (exercise as QuizExercise).quizMode !== undefined && (exercise as QuizExercise).quizMode !== QuizMode.INDIVIDUAL;
    }

    toggleSelection(id: number): void {
        const current = new Set(this.selectedIds());
        if (current.has(id)) {
            current.delete(id);
        } else {
            current.add(id);
        }
        this.selectedIds.set(current);
    }

    clearSelection(): void {
        this.selectedIds.set(new Set());
    }

    changeExerciseGroup(exercise: Exercise, newGroup: CourseExerciseGroup | undefined): void {
        if (newGroup && this.isQuizNonIndividual(exercise)) {
            // Mirrors the server-side rejection: synchronized/batched quizzes have a single shared run and cannot
            // share a group's timeline with other variants. The UI already disables the control for these exercises;
            // this is a defensive fallback (e.g. drag-and-drop in the group view).
            this.alertService.addErrorAlert('artemisApp.exerciseManagement.error.onlyIndividualQuiz');
            return;
        }
        const courseId = this.course()?.id;
        if (courseId !== undefined && exercise.id !== undefined) {
            this.exerciseVariantGroupService.setExerciseVariantGroup(courseId, exercise.id, newGroup?.id).subscribe({
                next: () => this.loadGroupsFromServer(courseId),
                error: (errorRes: HttpErrorResponse) => this.alertService.addErrorAlert(errorRes.error?.title ?? errorRes.message, errorRes.error?.message, errorRes.error?.params),
            });
        }
    }

    deleteSelected(): void {
        const ids = this.selectedIds();
        this.exercises.set(this.exercises().filter((e) => e.id === undefined || !ids.has(e.id)));
        this.clearSelection();
        this.buildBuckets();
    }

    editSelectedExercises(): void {}
    downloadReposSelected(): void {}
    consistencyCheckSelected(): void {}

    openCreateModal(): void {
        this.addModalMode.set('create');
        this.addModalVisible.set(true);
    }

    openImportModal(): void {
        this.addModalMode.set('import');
        this.addModalVisible.set(true);
    }

    openQuizExportDialog(): void {
        // Quiz exercises are the only exportable type. The develop quiz export page is shown as a modal component
        // (no dedicated route).
        const id = this.courseId();
        if (id === undefined) {
            return;
        }
        const dialogRef = this.dialogService.open(QuizExerciseExportComponent, {
            // Reactive title (re-translates on a language switch) instead of the static `header` string, which PrimeNG
            // resolves only once when the dialog opens.
            templates: { header: DialogTranslateHeaderComponent },
            width: '60rem',
            height: '40rem',
            // Let the component own its internal layout (scrollable table + pinned footer); the dialog content must not
            // scroll itself. Drop the content's bottom padding so the footer bar sits flush with the modal bottom.
            contentStyle: { overflow: 'hidden', display: 'flex', 'flex-direction': 'column', 'padding-bottom': '0' },
            modal: true,
            closable: true,
            closeOnEscape: true,
            draggable: false,
            data: { courseId: id, headerKey: 'artemisApp.exercise.exportAction' },
        });
        dialogRef?.onClose.subscribe((result: string | undefined) => {
            // "Back" in the export dialog returns to the manage-exercises modal (its default Create view).
            if (result === QUIZ_EXPORT_BACK) {
                this.addModalMode.set('create');
                this.addModalVisible.set(true);
            }
        });
    }

    onAddModalGroupCreate(): void {
        // Switch to (and remember) the group view via the shared handler, so a later editor visit returns here.
        this.onViewChange('group');
        // Open the edit modal with a blank draft — the user names the group there (the modal's Save stays disabled until
        // a title is entered) and it is only persisted when they save.
        this.openGroupEditDialog({ exercises: [] }, true);
    }

    onTableGroupChange(event: TableGroupChange): void {
        this.changeExerciseGroup(event.exercise, event.group);
    }

    onTableSelectionAllChange(bucket: Bucket, selectAll: boolean): void {
        const current = new Set(this.selectedIds());
        for (const exercise of bucket.exercises) {
            if (exercise.id !== undefined) {
                if (selectAll) {
                    current.add(exercise.id);
                } else {
                    current.delete(exercise.id);
                }
            }
        }
        this.selectedIds.set(current);
    }

    onTableRowsReordered(bucket: Bucket, reorderedExercises: Exercise[]): void {
        bucket.exercises = reorderedExercises;
        this.buckets.set([...this.buckets()]);
    }

    onExerciseUpdated(updated: Exercise): void {
        this.exercises.set(this.exercises().map((e) => (e.id === updated.id ? updated : e)));
        this.groups.set(
            this.groups().map((g) => ({
                ...g,
                exercises: (g.exercises ?? []).map((e) => (e.id === updated.id ? updated : e)),
            })),
        );
        this.buildBuckets();
    }

    onExerciseDeleted(deleted: Exercise): void {
        // Remove the deleted exercise from the flat list and from any group it belonged to, then rebuild the buckets
        // so it disappears from the view without requiring a page refresh.
        this.exercises.set(this.exercises().filter((e) => e.id !== deleted.id));
        this.groups.set(
            this.groups().map((g) => ({
                ...g,
                exercises: (g.exercises ?? []).filter((e) => e.id !== deleted.id),
            })),
        );
        if (deleted.id !== undefined && this.selectedIds().has(deleted.id)) {
            const remaining = new Set(this.selectedIds());
            remaining.delete(deleted.id);
            this.selectedIds.set(remaining);
        }
        this.buildBuckets();
    }

    private hasSearch(): boolean {
        return this.search().trim().length > 0;
    }

    private matches(exercise: Exercise): boolean {
        const term = this.search().trim().toLowerCase();
        return !term || (exercise.title ?? '').toLowerCase().includes(term);
    }

    private visibleExercises(): Exercise[] {
        return this.exercises().filter((exercise) => this.matches(exercise));
    }

    private sortExercises(exercises: Exercise[]): Exercise[] {
        return [...exercises].sort((a, b) => {
            const da = effectiveDate(a, this.owningGroup(a), 'dueDate');
            const db = effectiveDate(b, this.owningGroup(b), 'dueDate');
            return (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
        });
    }

    private buildBuckets(): void {
        switch (this.view()) {
            case 'group':
                this.buckets.set(this.buildGroupBuckets());
                break;
            case 'type':
                this.buckets.set(this.buildTypeBuckets());
                break;
            case 'week':
                this.buckets.set(this.buildWeekBuckets());
                break;
            case 'list':
                this.buckets.set(this.buildListBuckets());
                break;
        }
    }

    private buildListBuckets(): Bucket[] {
        const exercises = this.sortExercises(this.visibleExercises());
        if (exercises.length === 0) return [];
        return [{ id: 'all', title: this.translateService.instant('artemisApp.exerciseManagement.bucket.all'), exercises }];
    }

    private buildGroupBuckets(): Bucket[] {
        const groupedIds = new Set<number>();
        const searching = this.hasSearch();
        const buckets: Bucket[] = this.groups()
            .slice()
            .sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''))
            .map((group) => {
                const members = group.exercises ?? [];
                members.forEach((exercise) => exercise.id !== undefined && groupedIds.add(exercise.id));
                return {
                    id: `group-${group.id}`,
                    title: group.title ?? this.translateService.instant('artemisApp.exerciseManagement.bucket.group', { id: group.id }),
                    group,
                    exercises: this.sortExercises(members.filter((exercise) => this.matches(exercise))),
                };
            })
            .filter((bucket) => !searching || bucket.exercises.length > 0);

        const ungrouped = this.sortExercises(this.visibleExercises().filter((exercise) => exercise.id === undefined || !groupedIds.has(exercise.id)));
        if (ungrouped.length > 0) {
            buckets.push({ id: 'ungrouped', title: this.translateService.instant('artemisApp.exerciseManagement.bucket.ungrouped'), exercises: ungrouped });
        }
        return buckets;
    }

    private buildTypeBuckets(): Bucket[] {
        return TYPE_ORDER.map((type) => ({
            id: `type-${type}`,
            title: TYPE_TITLE_KEYS[type] ? this.translateService.instant(TYPE_TITLE_KEYS[type]) : type,
            icon: getIcon(type),
            exerciseType: type,
            exercises: this.sortExercises(this.visibleExercises().filter((exercise) => exercise.type === type)),
        })).filter((bucket) => bucket.exercises.length > 0);
    }

    private buildWeekBuckets(): Bucket[] {
        const startOf = (exercise: Exercise): dayjs.Dayjs | undefined =>
            effectiveDate(exercise, this.owningGroup(exercise), 'startDate') ?? effectiveDate(exercise, this.owningGroup(exercise), 'releaseDate');

        const visible = this.visibleExercises();
        const dated = visible.filter((exercise) => startOf(exercise));
        const undated = visible.filter((exercise) => !startOf(exercise));

        const base = dated.reduce<dayjs.Dayjs | undefined>((min, exercise) => {
            const date = startOf(exercise)!;
            return !min || date.isBefore(min) ? date : min;
        }, undefined);

        const byWeek = new Map<number, Exercise[]>();
        for (const exercise of dated) {
            const weekIndex = base ? startOf(exercise)!.diff(base, 'week') : 0;
            let list = byWeek.get(weekIndex);
            if (!list) {
                list = [];
                byWeek.set(weekIndex, list);
            }
            list.push(exercise);
        }

        const buckets: Bucket[] = [...byWeek.keys()]
            .sort((a, b) => a - b)
            .map((weekIndex) => ({
                id: `week-${weekIndex}`,
                title: this.translateService.instant('artemisApp.exerciseManagement.bucket.week', { number: weekIndex + 1 }),
                exercises: this.sortExercises(byWeek.get(weekIndex)!),
            }));

        if (undated.length > 0) {
            buckets.push({ id: 'unscheduled', title: this.translateService.instant('artemisApp.exerciseManagement.bucket.unscheduled'), exercises: this.sortExercises(undated) });
        }
        return buckets;
    }

    openGroupEditModal(id: number): void {
        const group = this.groups().find((g) => g.id === id);
        if (group) {
            this.openGroupEditDialog(group, false);
        }
    }

    /**
     * Opens the group-edit dialog via PrimeNG's {@link DialogService} (the declarative {@code <p-dialog>} mis-layered its
     * overlay on the first open). The dialog closes with the edited {@link CourseExerciseGroup} on save, or {@code undefined}
     * on cancel/dismiss; {@code isNew} selects the create vs. update persistence path in {@link onGroupEditModalSave}.
     */
    private openGroupEditDialog(group: CourseExerciseGroup, isNew: boolean): void {
        const dialogRef = this.dialogService.open(ExerciseGroupEditModalComponent, {
            inputValues: { group },
            width: '780px',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: { headerKey: 'artemisApp.exerciseManagement.groupEdit.header' },
            templates: { header: DialogTranslateHeaderComponent },
        });
        dialogRef?.onClose.subscribe((updated?: CourseExerciseGroup) => {
            if (updated) {
                this.onGroupEditModalSave(updated, isNew);
            }
        });
    }

    /** Opens the shared delete-confirmation dialog for a group; the actual deletion runs on confirm. */
    confirmDeleteGroup(group: CourseExerciseGroup): void {
        this.deleteDialogService.openDeleteDialog({
            entityTitle: group.title,
            deleteQuestion: 'artemisApp.exerciseVariantGroup.deleteDialog.question',
            deleteConfirmationText: 'artemisApp.exerciseVariantGroup.deleteDialog.typeNameToConfirm',
            translateValues: {},
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            requireConfirmationOnlyForAdditionalChecks: false,
            dialogError: this.groupDeleteError.asObservable(),
            delete: () => this.deleteGroup(group),
        });
    }

    /** Deletes the group. Member exercises are not deleted, they simply fall back into the "Ungrouped" bucket. */
    private deleteGroup(group: CourseExerciseGroup): void {
        const courseId = this.course()?.id;
        if (courseId !== undefined && group.id !== undefined) {
            this.exerciseVariantGroupService.deleteGroup(courseId, group.id).subscribe({
                next: () => {
                    this.groupDeleteError.next('');
                    this.loadGroupsFromServer(courseId);
                },
                error: (error: HttpErrorResponse) => this.groupDeleteError.next(error.message),
            });
        }
    }

    onGroupEditModalSave(updated: CourseExerciseGroup, isNew: boolean): void {
        const courseId = this.course()?.id;
        if (courseId === undefined) {
            return;
        }
        if (isNew) {
            this.exerciseVariantGroupService
                .createGroup(courseId, {
                    // The modal only emits a save with a non-empty, trimmed title (its Save button enforces this).
                    title: updated.title!,
                    maxPoints: updated.maxPoints,
                    releaseDate: updated.releaseDate,
                    startDate: updated.startDate,
                    dueDate: updated.dueDate,
                    assessmentDueDate: updated.assessmentDueDate,
                    exampleSolutionPublicationDate: updated.exampleSolutionPublicationDate,
                    buildAndTestStudentSubmissionsAfterDueDate: updated.buildAndTestStudentSubmissionsAfterDueDate,
                })
                .subscribe((dto) => {
                    const created = toCourseExerciseGroup(dto, this.exercisesById());
                    this.groups.set([...this.groups(), created]);
                    this.buildBuckets();
                });
            return;
        }

        if (updated.id !== undefined) {
            this.exerciseVariantGroupService
                .updateGroup(courseId, {
                    id: updated.id,
                    title: updated.title!,
                    maxPoints: updated.maxPoints,
                    releaseDate: updated.releaseDate,
                    startDate: updated.startDate,
                    dueDate: updated.dueDate,
                    assessmentDueDate: updated.assessmentDueDate,
                    exampleSolutionPublicationDate: updated.exampleSolutionPublicationDate,
                    buildAndTestStudentSubmissionsAfterDueDate: updated.buildAndTestStudentSubmissionsAfterDueDate,
                })
                .subscribe((dto) => {
                    const mapped = toCourseExerciseGroup(dto, this.exercisesById());
                    this.groups.set(this.groups().map((g) => (g.id === updated.id ? { ...g, ...mapped } : g)));
                    this.buildBuckets();
                });
        }
    }

    /** Recomputes the client-derived quiz `status` / `quizStarted` flags (the server does not serialize them). */
    private applyQuizClientState(quiz: QuizExercise): void {
        quiz.status = this.quizExerciseService.getStatus(quiz);
        quiz.quizStarted = quiz.status === QuizStatus.ACTIVE;
    }

    /**
     * The /with-exercises response does not load the quizBatches association, so batches would be missing after a
     * refresh. Fetch them from the dedicated quiz endpoint and merge them into the loaded quiz exercises, then
     * recompute the batch-dependent status. Replaced quizzes get a fresh object reference so the row's signal input
     * reacts (an in-place mutation would not re-render until a view rebuild); both the flat list and the groups are
     * pointed at the new objects, mirroring {@link onExerciseUpdated}.
     */
    private loadQuizBatches(courseId: number): void {
        this.quizExerciseService.findForCourse(courseId).subscribe((response) => {
            // quizInfoById carries both quizBatches (for status computation) and isEditable (from the server,
            // which uses the authoritative DB check — client-side status alone can miss cases like INDIVIDUAL
            // mode quizzes where student batches are started but the quiz is not visibleToStudents yet).
            const quizInfoById = new Map((response.body ?? []).map((quiz) => [quiz.id, { quizBatches: quiz.quizBatches, isEditable: quiz.isEditable }]));
            const replacements = new Map<number, Exercise>();
            const merged = this.exercises().map((exercise) => {
                if (exercise.type === ExerciseType.QUIZ && exercise.id !== undefined && quizInfoById.has(exercise.id)) {
                    const quiz = { ...(exercise as QuizExercise) } as QuizExercise;
                    const info = quizInfoById.get(exercise.id)!;
                    quiz.quizBatches = info.quizBatches;
                    quiz.isEditable = info.isEditable;
                    this.applyQuizClientState(quiz);
                    replacements.set(exercise.id, quiz);
                    return quiz;
                }
                return exercise;
            });
            if (replacements.size === 0) {
                return;
            }
            this.exercises.set(merged);
            this.groups.set(
                this.groups().map((group) => ({
                    ...group,
                    exercises: (group.exercises ?? []).map((exercise) => (exercise.id !== undefined && replacements.has(exercise.id) ? replacements.get(exercise.id)! : exercise)),
                })),
            );
            this.buildBuckets();
        });
    }

    /** Loads the course's variant groups from the server and maps them to the view model. */
    private loadGroupsFromServer(courseId: number): void {
        this.exerciseVariantGroupService.getGroupsForCourse(courseId).subscribe((dtos) => {
            // Build a map from exercise id to its new group reference so we can update exerciseVariantGroup.
            const refByExerciseId = new Map<number, ExerciseVariantGroupReference>();
            const groupDtoByExerciseId = new Map<number, ExerciseVariantGroupDTO>();
            for (const dto of dtos) {
                const ref: ExerciseVariantGroupReference = { id: dto.id, title: dto.title, maxPoints: dto.maxPoints };
                for (const id of dto.exerciseIds ?? []) {
                    refByExerciseId.set(id, ref);
                    groupDtoByExerciseId.set(id, dto);
                }
            }
            const now = dayjs();
            // Spread exercises whose group membership changed so dependent signal computeds see new references.
            // Also apply the group's shared timeline and recompute quiz client state so lifecycle buttons reflect
            // the new group's dates immediately (without a page reload).
            const updatedExercises = this.exercises().map((ex) => {
                if (ex.id === undefined) return ex;
                const newRef = refByExerciseId.get(ex.id);
                if (newRef?.id === ex.exerciseVariantGroup?.id) return ex;
                const groupDto = groupDtoByExerciseId.get(ex.id);
                const updated: Exercise = {
                    ...ex,
                    exerciseVariantGroup: newRef,
                    releaseDate: groupDto?.releaseDate,
                    startDate: groupDto?.startDate,
                    dueDate: groupDto?.dueDate,
                    assessmentDueDate: groupDto?.assessmentDueDate,
                    exampleSolutionPublicationDate: groupDto?.exampleSolutionPublicationDate,
                };
                if (updated.type === ExerciseType.QUIZ) {
                    const quiz = updated as QuizExercise;
                    const startDate = quiz.startDate ?? quiz.releaseDate;
                    if (startDate !== undefined) {
                        quiz.visibleToStudents = startDate.isBefore(now);
                    }
                    if (quiz.dueDate !== undefined) {
                        quiz.quizEnded = quiz.dueDate.isBefore(now);
                    }
                    this.applyQuizClientState(quiz);
                }
                return updated;
            });
            this.exercises.set(updatedExercises);
            const exercisesById = new Map(updatedExercises.filter((e) => e.id !== undefined).map((e) => [e.id!, e]));
            this.groups.set(dtos.map((dto) => toCourseExerciseGroup(dto, exercisesById)));
            this.buildBuckets();
        });
    }

    private exercisesById(): Map<number, Exercise> {
        return new Map(
            this.exercises()
                .filter((exercise) => exercise.id !== undefined)
                .map((exercise) => [exercise.id!, exercise]),
        );
    }
}
