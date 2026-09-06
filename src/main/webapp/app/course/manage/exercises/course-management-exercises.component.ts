import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, catchError, forkJoin, map, of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseExportComponent } from 'app/quiz/manage/export/quiz-exercise-export.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FormsModule } from '@angular/forms';
import { TumUiButtonComponent, TumUiButtonDirective, TumUiMessageComponent, TumUiPanelComponent, TumUiSelectButtonComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import {
    faCalendarDays,
    faCheckDouble,
    faCircleInfo,
    faCode,
    faFileExport,
    faFileImport,
    faLayerGroup,
    faList,
    faPen,
    faPlus,
    faTrash,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseEditSelectedComponent } from 'app/programming/manage/edit-selected/programming-exercise-edit-selected.component';
import { ConsistencyCheckComponent } from 'app/programming/manage/consistency-check/consistency-check.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/programming/manage/assess/repo-export/export-button/programming-assessment-repo-export-button.component';
import { ExerciseScoresExportButtonComponent } from 'app/exercise/exercise-scores/export-button/exercise-scores-export-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import {
    ExerciseVariantGroupService,
    isPersistableGroup,
    toCourseExerciseGroup,
    toCreateGroupPayload,
    toUpdateGroupPayload,
} from 'app/course/manage/exercises/exercise-variant-group.service';
import { CourseExerciseCard, ExerciseManagementView, buildCourseExerciseCards } from 'app/course/manage/exercises/course-exercise-cards';
import { ExerciseGroupSyncService } from 'app/course/manage/exercises/exercise-group-sync.service';
import { ExerciseTableComponent, TableGroupChange } from 'app/course/manage/exercises/exercise-row/exercise-table.component';
import { AddModalMode, ExerciseAddModalComponent } from 'app/course/manage/exercises/create-modal/exercise-add-modal.component';
import { ExerciseGroupEditModalComponent } from 'app/course/manage/exercises/group-edit-modal/exercise-group-edit-modal.component';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarToolbarDirective } from 'app/course/shared/directives/course-title-bar-toolbar.directive';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { cloneWith, hydrate } from 'app/foundation/util/deep-clone.util';

/** Local-storage key under which the last-selected view is remembered, so closing an exercise editor returns to it. */
const VIEW_STORAGE_KEY = 'artemis.exerciseManagement.view';

@Component({
    selector: 'jhi-course-management-exercises',
    templateUrl: './course-management-exercises.component.html',
    styleUrl: './course-management-exercises.component.scss',
    imports: [
        FormsModule,
        TumUiSelectButtonComponent,
        TumUiPanelComponent,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiMessageComponent,
        TumUiTooltipDirective,
        FaIconComponent,
        ExerciseTableComponent,
        ExerciseAddModalComponent,
        ExerciseGroupEditModalComponent,
        ConsistencyCheckComponent,
        QuizExerciseExportComponent,
        ProgrammingExerciseEditSelectedComponent,
        ProgrammingAssessmentRepoExportButtonComponent,
        ExerciseScoresExportButtonComponent,
        DeleteButtonDirective,
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
    protected readonly faWrench = faWrench;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly faLayerGroup = faLayerGroup;
    protected readonly faList = faList;
    protected readonly ExerciseType = ExerciseType;

    readonly viewOptions: { labelKey: string; value: ExerciseManagementView; icon: IconProp }[] = [
        { labelKey: 'artemisApp.exerciseManagement.view.list', value: 'list', icon: faList },
        { labelKey: 'artemisApp.exerciseManagement.view.type', value: 'type', icon: faCode },
        { labelKey: 'artemisApp.exerciseManagement.view.week', value: 'week', icon: faCalendarDays },
        { labelKey: 'artemisApp.exerciseManagement.view.group', value: 'group', icon: faLayerGroup },
    ];

    readonly view = signal<ExerciseManagementView>('type');
    readonly search = signal('');

    readonly course = signal<Course | undefined>(undefined);
    readonly exercises = signal<Exercise[]>([]);
    readonly groups = signal<CourseExerciseGroup[]>([]);
    readonly cards = signal<CourseExerciseCard[]>([]);
    /** Whether the initial load has finished, so the empty state is not shown while the exercises are still coming. */
    readonly loaded = signal(false);

    readonly selectedIds = signal<Set<number>>(new Set());
    readonly addModalVisible = signal(false);
    readonly addModalMode = signal<AddModalMode>('create');

    // Declarative-modal state: a `show*` visibility flag plus, where the modal's input is required, its data signal.
    readonly showGroupEdit = signal(false);
    readonly groupEditGroup = signal<CourseExerciseGroup | undefined>(undefined);
    /** Whether the group-edit modal is creating (vs. updating) — chooses the persistence path on save. */
    protected readonly groupEditIsNew = signal(false);
    readonly showConsistencyCheck = signal(false);
    readonly consistencyExercises = signal<ProgrammingExercise[]>([]);
    readonly showQuizExport = signal(false);
    readonly showEditSelected = signal(false);
    readonly editSelectedData = signal<ProgrammingExercise[]>([]);

    readonly isGroup = computed(() => this.view() === 'group');
    /** Deleting a group requires the same permission as deleting an exercise: instructor (or admin) on the course. */
    readonly canDeleteGroups = computed(() => this.course()?.isAtLeastInstructor ?? false);
    /** Ids of all rendered cards, so each group's exercise table is a connected CDK drop target for the others. */
    readonly dropListIds = computed(() => this.cards().map((card) => card.id));
    readonly exerciseCount = computed(() => this.exercises().length);
    readonly courseId = computed(() => this.course()?.id);
    readonly selectedCount = computed(() => this.selectedIds().size);

    readonly selectedTypes = computed(() => {
        const ids = this.selectedIds();
        return new Set(
            this.exercises()
                .filter((e) => e.id !== undefined && ids.has(e.id))
                .map((e) => e.type),
        );
    });
    readonly allSelectedAreProgramming = computed(() => {
        const t = this.selectedTypes();
        return t.size === 1 && t.has(ExerciseType.PROGRAMMING);
    });

    /**
     * Whether the selection contains a variant-group member. The edit-selected modal writes each exercise's timeline
     * directly, which would desync a member from its group, so only bulk timeline editing is blocked for those.
     */
    readonly selectionHasGroupMember = computed(() => this.selectedExercises().some((exercise) => exercise.exerciseVariantGroup?.id !== undefined));

    /** The currently selected exercises, resolved from {@link selectedIds} to the full exercise objects. */
    readonly selectedExercises = computed(() => {
        const ids = this.selectedIds();
        return this.exercises().filter((exercise) => exercise.id !== undefined && ids.has(exercise.id));
    });
    /** The selected exercises narrowed to programming exercises — the mass actions below only apply to those. */
    readonly selectedProgrammingExercises = computed(() => this.selectedExercises().filter((exercise) => exercise.type === ExerciseType.PROGRAMMING) as ProgrammingExercise[]);

    private readonly route = inject(ActivatedRoute);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly quizExerciseService = inject(QuizExerciseService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly textExerciseService = inject(TextExerciseService);
    private readonly fileUploadExerciseService = inject(FileUploadExerciseService);
    private readonly modelingExerciseService = inject(ModelingExerciseService);
    private readonly translateService = inject(TranslateService);
    private readonly exerciseVariantGroupService = inject(ExerciseVariantGroupService);
    private readonly groupSync = inject(ExerciseGroupSyncService);
    private readonly deleteDialogService = inject(DeleteDialogService);
    private readonly alertService = inject(AlertService);
    private readonly localStorageService = inject(LocalStorageService);
    private readonly profileService = inject(ProfileService);
    private readonly destroyRef = inject(DestroyRef);

    /** Under LocalCI repositories and build plans live inside Artemis, so the delete dialog offers no external cleanup checks. */
    protected readonly localCIEnabled = signal(true);

    private readonly groupDeleteError = new Subject<string>();
    private readonly selectedDeleteError = new Subject<string>();
    /** Exposed to the bulk-delete button (jhiDeleteButton) so the shared delete dialog can surface deletion errors. */
    readonly selectedDeleteError$ = this.selectedDeleteError.asObservable();

    constructor() {
        // Restore the last-selected view, validated so a stale entry falls back to the default.
        const storedView = this.localStorageService.retrieve<ExerciseManagementView>(VIEW_STORAGE_KEY);
        if (storedView && this.viewOptions.some((option) => option.value === storedView)) {
            this.view.set(storedView);
        }
        this.localCIEnabled.set(this.profileService.isProfileActive(PROFILE_LOCALCI));
    }

    ngOnInit(): void {
        // Card titles are resolved eagerly via TranslateService.instant, so rebuild them on a language switch.
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.rebuildCards());

        this.route.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
            }
            if (course?.id) {
                this.loadCourseExercises(course.id);
            } else {
                this.rebuildCards();
                this.loaded.set(true);
            }
        });
    }

    /** Loads the course's exercises (with access rights, quiz state, groups and batches) and rebuilds the cards. */
    private loadCourseExercises(courseId: number): void {
        this.courseManagementService.findWithExercises(courseId).subscribe({
            next: (response) => {
                const loadedCourse = response.body;
                const exercises = loadedCourse?.exercises ?? [];
                exercises.forEach((exercise) => {
                    exercise.isAtLeastTutor = loadedCourse?.isAtLeastTutor;
                    exercise.isAtLeastEditor = loadedCourse?.isAtLeastEditor;
                    exercise.isAtLeastInstructor = loadedCourse?.isAtLeastInstructor;
                    if (exercise.type === ExerciseType.QUIZ) {
                        this.groupSync.applyQuizClientState(exercise);
                    }
                });
                this.exercises.set(exercises);
                // The variant-group endpoints are editor-only, and this page is also reachable by tutors.
                if (loadedCourse?.isAtLeastEditor) {
                    this.loadGroupsFromServer(courseId);
                } else {
                    // Clear groups left over from a previously shown course, since the instance is reused.
                    this.groups.set([]);
                }
                this.rebuildCards();
                this.loaded.set(true);
                this.loadQuizBatches(courseId);
            },
        });
    }

    onViewChange(view: ExerciseManagementView): void {
        this.view.set(view);
        // Remember the selection so it is restored when the component is re-instantiated (e.g. after closing an editor).
        this.localStorageService.store(VIEW_STORAGE_KEY, view);
        this.rebuildCards();
    }

    onSearchChange(term: string): void {
        this.search.set(term);
        this.rebuildCards();
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
            // Defensive fallback (e.g. drag-and-drop) for what the server rejects and the UI already disables.
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

    /**
     * Deletes every selected exercise via its type-specific service, invoked by the bulk-delete button on confirm.
     * Reloads afterwards so the view matches the server even on a partial failure.
     */
    deleteSelectedExercises(event: { [key: string]: boolean } = {}): void {
        const exercisesToDelete = this.selectedExercises();
        const courseId = this.course()?.id;
        if (exercisesToDelete.length === 0 || courseId === undefined) {
            this.selectedDeleteError.next('');
            return;
        }
        // catchError turns a rejected delete into an emitted value, so forkJoin waits for the whole batch
        // instead of unsubscribing the remaining deletes on the first failure.
        const deletionObservables = exercisesToDelete.map((exercise) =>
            this.deleteObservableFor(exercise, event).pipe(
                map(() => undefined),
                catchError((error: HttpErrorResponse) => of(error)),
            ),
        );
        forkJoin(deletionObservables).subscribe({
            next: (results) => {
                const firstError = results.find((result): result is HttpErrorResponse => result !== undefined);
                this.selectedDeleteError.next(firstError?.message ?? '');
                // Some exercises may already be gone; reload so the view matches the server (even on a partial failure).
                this.clearSelection();
                this.loadCourseExercises(courseId);
            },
        });
    }

    /** Resolves the type-specific deletion request for a single exercise, forwarding the delete dialog's cleanup checks. */
    private deleteObservableFor(exercise: Exercise, event: { [key: string]: boolean }): Observable<HttpResponse<void>> {
        switch (exercise.type) {
            case ExerciseType.PROGRAMMING:
                // The cleanup checks are only offered on non-LocalCI setups, so the flags default to false.
                return this.programmingExerciseService.delete(exercise.id!, event.deleteStudentReposBuildPlans ?? false, event.deleteBaseReposBuildPlans ?? false);
            case ExerciseType.QUIZ:
                return this.quizExerciseService.delete(exercise.id!);
            case ExerciseType.TEXT:
                return this.textExerciseService.delete(exercise.id!);
            case ExerciseType.FILE_UPLOAD:
                return this.fileUploadExerciseService.delete(exercise.id!);
            case ExerciseType.MODELING:
                return this.modelingExerciseService.delete(exercise.id!);
            default:
                return new Observable<HttpResponse<void>>((subscriber) => subscriber.complete());
        }
    }

    /** Opens the "edit selected" modal to apply a common timeline to all selected programming exercises at once. */
    editSelectedExercises(): void {
        // Defensive: the button is already disabled, and a group member routed through here would desync.
        if (this.selectionHasGroupMember()) {
            return;
        }
        this.editSelectedData.set(this.selectedProgrammingExercises());
        this.showEditSelected.set(true);
    }

    /** After the "edit selected" modal saved, reload so the updated dates show in the table and cards. */
    protected onEditSelectedSaved(): void {
        const courseId = this.course()?.id;
        if (courseId !== undefined) {
            this.loadCourseExercises(courseId);
        }
    }

    /** Runs a consistency check over the selected programming exercises and shows the results in a dialog. */
    consistencyCheckSelected(): void {
        this.consistencyExercises.set(this.selectedProgrammingExercises());
        this.showConsistencyCheck.set(true);
    }

    openCreateModal(): void {
        this.addModalMode.set('create');
        this.addModalVisible.set(true);
    }

    openImportModal(): void {
        this.addModalMode.set('import');
        this.addModalVisible.set(true);
    }

    openQuizExportDialog(): void {
        if (this.courseId() === undefined) {
            return;
        }
        this.showQuizExport.set(true);
    }

    /** "Back" in the export dialog returns to the manage-exercises modal (its default Create view). */
    protected onQuizExportBack(): void {
        this.addModalMode.set('create');
        this.addModalVisible.set(true);
    }

    onAddModalGroupCreate(): void {
        // Switch to (and remember) the group view, so a later editor visit returns here.
        this.onViewChange('group');
        // Blank draft: the user names the group in the modal, and it is only persisted on save.
        this.openGroupEditDialog({ exercises: [] }, true);
    }

    onTableGroupChange(event: TableGroupChange): void {
        this.changeExerciseGroup(event.exercise, event.group);
    }

    onTableSelectionAllChange(card: CourseExerciseCard, selectAll: boolean): void {
        const current = new Set(this.selectedIds());
        for (const exercise of card.exercises) {
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

    onExerciseUpdated(updated: Exercise): void {
        this.exercises.set(this.exercises().map((e) => (e.id === updated.id ? updated : e)));
        this.groups.set(this.groups().map((g) => cloneWith(g, { exercises: (g.exercises ?? []).map((e) => (e.id === updated.id ? updated : e)) })));
        this.rebuildCards();
    }

    onExerciseDeleted(deleted: Exercise): void {
        // Prune the exercise locally (flat list + its group) so it disappears without a reload.
        this.exercises.set(this.exercises().filter((e) => e.id !== deleted.id));
        this.groups.set(this.groups().map((g) => cloneWith(g, { exercises: (g.exercises ?? []).filter((e) => e.id !== deleted.id) })));
        if (deleted.id !== undefined && this.selectedIds().has(deleted.id)) {
            const remaining = new Set(this.selectedIds());
            remaining.delete(deleted.id);
            this.selectedIds.set(remaining);
        }
        this.rebuildCards();
    }

    /** Rebuilds the view's panels for the current view mode, exercises, groups and search term. */
    private rebuildCards(): void {
        this.cards.set(
            buildCourseExerciseCards(this.view(), {
                exercises: this.exercises(),
                groups: this.groups(),
                searchTerm: this.search(),
                translate: (key, interpolateParams) => this.translateService.instant(key, interpolateParams),
            }),
        );
    }

    openGroupEditModal(id: number): void {
        const group = this.groups().find((g) => g.id === id);
        if (group) {
            this.openGroupEditDialog(group, false);
        }
    }

    /** Opens the group-edit modal. {@code isNew} selects the create vs. update path in {@link onGroupEditModalSave}. */
    private openGroupEditDialog(group: CourseExerciseGroup, isNew: boolean): void {
        this.groupEditGroup.set(group);
        this.groupEditIsNew.set(isNew);
        this.showGroupEdit.set(true);
    }

    /** Applies the group-edit modal's result, choosing the create vs. update path via the remembered {@code isNew} flag. */
    protected onGroupEditSaved(updated: CourseExerciseGroup): void {
        this.onGroupEditModalSave(updated, this.groupEditIsNew());
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

    /** Deletes the group. Member exercises are not deleted, they simply fall back into the "Ungrouped" card. */
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
        // The modal's Save button already enforces a non-empty title; narrowing makes that explicit.
        if (courseId === undefined || !isPersistableGroup(updated)) {
            return;
        }
        if (isNew) {
            this.exerciseVariantGroupService.createGroup(courseId, toCreateGroupPayload(updated)).subscribe({
                next: (dto) => {
                    const created = toCourseExerciseGroup(dto, this.exercisesById());
                    this.groups.set([...this.groups(), created]);
                    this.rebuildCards();
                },
                error: (errorRes: HttpErrorResponse) => this.alertService.addErrorAlert(errorRes.error?.title ?? errorRes.message, errorRes.error?.message, errorRes.error?.params),
            });
            return;
        }

        const groupId = updated.id;
        if (groupId !== undefined) {
            this.exerciseVariantGroupService.updateGroup(courseId, toUpdateGroupPayload(updated, groupId)).subscribe({
                next: (dto) => {
                    // Mirror the server-side re-sync onto each member, so dates and quiz badges update without a reload.
                    const memberIds = new Set(dto.exerciseIds ?? []);
                    const now = dayjs();
                    const refreshedExercises = this.exercises().map((ex) =>
                        ex.id !== undefined && memberIds.has(ex.id) ? this.groupSync.applyGroupTimelineToMember(ex, dto, now) : ex,
                    );
                    this.exercises.set(refreshedExercises);
                    const refreshedById = new Map(refreshedExercises.filter((e) => e.id !== undefined).map((e) => [e.id!, e]));
                    const mapped = toCourseExerciseGroup(dto, refreshedById);
                    // Merge rather than replace: the DTO carries no `order`, so mapping alone drops the display order.
                    this.groups.set(this.groups().map((group) => (group.id === updated.id ? hydrate(new CourseExerciseGroup(), group, mapped) : group)));
                    this.rebuildCards();
                },
                error: (errorRes: HttpErrorResponse) => this.alertService.addErrorAlert(errorRes.error?.title ?? errorRes.message, errorRes.error?.message, errorRes.error?.params),
            });
        }
    }

    /** The /with-exercises response omits the quizBatches association, so fetch it from the quiz endpoint and merge. */
    private loadQuizBatches(courseId: number): void {
        this.quizExerciseService.findForCourse(courseId).subscribe((response) => {
            const merged = this.groupSync.mergeQuizInfo(this.exercises(), this.groups(), response.body ?? []);
            if (!merged) {
                return;
            }
            this.exercises.set(merged.exercises);
            this.groups.set(merged.groups);
            this.rebuildCards();
        });
    }

    /** Loads the course's variant groups from the server and merges them into the exercise list and view model. */
    private loadGroupsFromServer(courseId: number): void {
        this.exerciseVariantGroupService.getGroupsForCourse(courseId).subscribe({
            next: (dtos) => {
                const merged = this.groupSync.mergeGroupsIntoExercises(this.exercises(), dtos);
                this.exercises.set(merged.exercises);
                this.groups.set(merged.groups);
                this.rebuildCards();
            },
            error: (errorRes: HttpErrorResponse) => {
                // Drop the previous course's groups, or they would be rendered against the new exercise list.
                this.groups.set([]);
                this.rebuildCards();
                this.alertService.addErrorAlert(errorRes.error?.title ?? errorRes.message, errorRes.error?.message, errorRes.error?.params);
            },
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
