import { TumAetUiButtonComponent, TumAetUiDialogComponent, TumAetUiPanelComponent } from '@tumaet/ui-angular';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HttpErrorResponse } from '@angular/common/http';
import { isErrorAlert, onError } from 'app/foundation/util/global.utils';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { faAngleDown, faAngleUp, faFileImport, faLayerGroup, faPen, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ExamExerciseImportDialogComponent } from 'app/exam/manage/exercise-groups/exercise-import-dialog/exam-exercise-import-dialog.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_FILEUPLOAD, MODULE_FEATURE_MODELING, MODULE_FEATURE_TEXT, PROFILE_LOCALCI } from 'app/app.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { ExamExerciseTableComponent, ExamTableGroupChange } from 'app/exam/manage/exercise-groups/exercise-table/exam-exercise-table.component';
import { ExamExerciseGroupEditModalComponent } from 'app/exam/manage/exercise-groups/group-edit-modal/exam-exercise-group-edit-modal.component';
import { ExamExerciseTypePickerComponent, ExamExerciseTypePickerMode } from 'app/exam/manage/exercise-groups/exercise-type-picker/exam-exercise-type-picker.component';
import { ExamDeleteDialogComponent } from 'app/exam/shared/delete-dialog/exam-delete-dialog.component';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { deepClone } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
    styleUrls: ['./exercise-groups.component.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        ArtemisTranslatePipe,
        TumAetUiPanelComponent,
        TumAetUiButtonComponent,
        TumAetUiDialogComponent,
        ExamImportComponent,
        ExamExerciseImportDialogComponent,
        ExamDeleteDialogComponent,
        ExamExerciseTableComponent,
        ExamExerciseGroupEditModalComponent,
        ExamExerciseTypePickerComponent,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleDirective,
    ],
})
export class ExerciseGroupsComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseGroupService = inject(ExerciseGroupService);
    private examManagementService = inject(ExamManagementService);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private profileService = inject(ProfileService);

    readonly courseId = signal<number>(undefined!);
    course = signal<Course | undefined>(undefined);
    readonly examId = signal<number>(undefined!);
    exam = signal<Exam | undefined>(undefined);
    exerciseGroups = signal<ExerciseGroup[] | undefined>(undefined);
    readonly exerciseGroupCount = computed(() => this.exerciseGroups()?.length ?? 0);
    dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();
    latestIndividualEndDate = signal<dayjs.Dayjs | undefined>(undefined);

    // Guards against reorder-response races: every arrow click fires an independent PUT, and a stale response
    // arriving after a newer one would re-apply an older order. While a save is in flight, further reorder actions
    // are ignored (no queueing) so responses can never interleave.
    orderSavePending = signal(false);

    localCIEnabled = signal(true);
    disabledExerciseTypes: ExerciseType[] = [];

    /** Ids of every group's drop list, so exercises can be dragged between any two group tables. */
    readonly dropListIds = computed(() => (this.exerciseGroups() ?? []).map((group) => this.groupDropListId(group.id)));

    groupDropListId(groupId: number | undefined): string {
        return `exercise-group-${groupId}`;
    }

    readonly typePickerVisible = signal(false);
    readonly typePickerGroupId = signal<number | undefined>(undefined);
    readonly typePickerMode = signal<ExamExerciseTypePickerMode>('create');

    readonly groupEditVisible = signal(false);
    protected readonly groupImportVisible = signal(false);

    protected readonly exerciseImportVisible = signal(false);
    protected readonly exerciseImportType = signal<ExerciseType | undefined>(undefined);
    private readonly exerciseImportGroup = signal<ExerciseGroup | undefined>(undefined);

    protected readonly deleteGroupVisible = signal(false);
    protected readonly groupToDelete = signal<ExerciseGroup | undefined>(undefined);
    protected readonly deleteGroupQuestion = computed(() =>
        this.groupToDelete() && this.containsProgrammingExercise(this.groupToDelete()!)
            ? 'artemisApp.examManagement.exerciseGroup.delete.questionLocalVC'
            : 'artemisApp.examManagement.exerciseGroup.delete.question',
    );
    /** Groups with a programming exercise also offer to clean up build plans, unless LocalCI is active, which needs none. */
    protected readonly deleteGroupChecks = computed<Record<string, string>>(() => {
        const group = this.groupToDelete();
        const checks: Record<string, string> = {};
        if (group && this.containsProgrammingExercise(group) && !this.localCIEnabled()) {
            checks['deleteStudentReposBuildPlans'] = 'artemisApp.programmingExercise.delete.studentReposBuildPlans';
            checks['deleteBaseReposBuildPlans'] = 'artemisApp.programmingExercise.delete.baseReposBuildPlans';
        }
        return checks;
    });
    readonly groupEditTarget = signal<ExerciseGroup | undefined>(undefined);
    /** Selects the create vs. update persistence path in {@link onGroupEditSaved}. */
    readonly groupEditIsNew = signal(false);

    // Icons
    faPlus = faPlus;
    faTrash = faTrash;
    faPen = faPen;
    faFileImport = faFileImport;
    faLayerGroup = faLayerGroup;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    /**
     * Initialize the courseId and examId. Get all exercise groups for the exam.
     */
    ngOnInit(): void {
        this.courseId.set(Number(this.route.snapshot.paramMap.get('courseId')));
        this.examId.set(Number(this.route.snapshot.paramMap.get('examId')));
        // Only take action when a response was received for both requests
        forkJoin([this.loadExerciseGroups(), this.loadLatestIndividualEndDateOfExam()]).subscribe({
            next: ([examRes, examInfoDTO]) => {
                this.exam.set(examRes.body!);
                this.exerciseGroups.set(this.exam()!.exerciseGroups);
                this.course.set(this.exam()!.course);
                this.latestIndividualEndDate.set(examInfoDTO ? examInfoDTO.body!.latestIndividualEndDate : undefined);
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
        this.localCIEnabled.set(this.profileService.isProfileActive(PROFILE_LOCALCI));
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_TEXT)) {
            this.disabledExerciseTypes.push(ExerciseType.TEXT);
        }
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_MODELING)) {
            this.disabledExerciseTypes.push(ExerciseType.MODELING);
        }
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_FILEUPLOAD)) {
            this.disabledExerciseTypes.push(ExerciseType.FILE_UPLOAD);
        }
    }

    /**
     * Load the latest individual end date of the exam. If this the HTTP response is erroneous, an observables emitting
     * null will be returned
     */
    loadLatestIndividualEndDateOfExam() {
        return this.examManagementService.getLatestIndividualEndDateOfExam(this.courseId(), this.examId()).pipe(
            // When the exam start date was not set properly an error will be thrown.
            // Catch this in the inner observable otherwise forkJoin won't return data
            catchError(() => {
                return of(null);
            }),
        );
    }

    /**
     * Load all exercise groups of the current exam.
     */
    loadExerciseGroups() {
        return this.examManagementService.find(this.courseId(), this.examId(), true);
    }

    /**
     * Remove the exercise with the given exerciseId from the exercise group with the given exerciseGroupId.
     * @param exerciseId
     * @param exerciseGroupId
     */
    removeExercise(exerciseId: number, exerciseGroupId: number) {
        const exerciseGroups = this.exerciseGroups();
        if (!exerciseGroups) {
            return;
        }
        // Replace the affected group with a clone rather than mutating it: the signal only notifies on a new reference.
        this.exerciseGroups.set(
            exerciseGroups.map((group) => {
                if (group.id !== exerciseGroupId) {
                    return group;
                }
                const updated = deepClone(group);
                updated.exercises = (updated.exercises ?? []).filter((exercise) => exercise.id !== exerciseId);
                return updated;
            }),
        );
    }

    /**
     * Delete the exercise group with the given id.
     * @param exerciseGroupId
     * @param event representation of users choices to delete the student repositories and base repositories
     */
    deleteExerciseGroup(exerciseGroupId: number, event: { [key: string]: boolean }) {
        this.exerciseGroupService.delete(this.courseId(), this.examId(), exerciseGroupId, event.deleteStudentReposBuildPlans, event.deleteBaseReposBuildPlans).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'exerciseGroupOverviewModification',
                    content: 'Deleted an exercise group',
                });
                this.dialogErrorSource.next('');
                this.exerciseGroups.set(this.exerciseGroups()!.filter((exerciseGroup) => exerciseGroup.id !== exerciseGroupId));
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Opens the import dialog for a specific exercise type
     * @param exerciseGroup The current exercise group
     * @param exerciseType The exercise type you want to import
     */
    openImportModal(exerciseGroup: ExerciseGroup, exerciseType: ExerciseType) {
        this.exerciseImportGroup.set(exerciseGroup);
        this.exerciseImportType.set(exerciseType);
        this.exerciseImportVisible.set(true);
    }

    /**
     * Closes the exercise import dialog and continues on the import route of the exercise type: with the chosen exercise, or with
     * the exercise read from a file.
     * @param result the exercise to import
     */
    protected onExerciseImported(result: Exercise): void {
        const exerciseGroup = this.exerciseImportGroup();
        const exerciseType = this.exerciseImportType();
        this.exerciseImportVisible.set(false);
        if (!exerciseGroup || !exerciseType) {
            return;
        }
        const importBaseRoute = ['/course-management', this.courseId(), 'exams', this.examId(), 'exercise-groups', exerciseGroup.id, `${exerciseType}-exercises`];
        if (result.id) {
            importBaseRoute.push('import', result.id);
            void this.router.navigate(importBaseRoute);
        } else {
            // we know it must be a programming exercise, because only programming exercises can be imported from a file
            importBaseRoute.push('import-from-file');
            void this.router.navigate(importBaseRoute, {
                state: {
                    programmingExerciseForImportFromFile: result,
                },
            });
        }
    }

    /**
     * Opens the per-group exercise-type picker, either to create a new exercise or to import one.
     * @param groupId the id of the exercise group the exercise should be created/imported into
     * @param mode 'create' opens the type picker on the create routes, 'import' delegates to the import dialog
     */
    openTypePicker(groupId: number, mode: ExamExerciseTypePickerMode): void {
        this.typePickerGroupId.set(groupId);
        this.typePickerMode.set(mode);
        this.typePickerVisible.set(true);
    }

    /** Forwards the type picker's import request to the existing import dialog for the remembered group. */
    onTypePickerImport(exerciseType: ExerciseType): void {
        const group = this.exerciseGroups()?.find((g) => g.id === this.typePickerGroupId());
        if (group) {
            this.openImportModal(group, exerciseType);
        }
    }

    /**
     * Opens the title/mandatory-only group-edit dialog for the given group.
     * @param groupId the id of the exercise group to edit
     */
    openGroupEditModal(groupId: number): void {
        const group = this.exerciseGroups()?.find((g) => g.id === groupId);
        if (group) {
            this.groupEditTarget.set(group);
            this.groupEditIsNew.set(false);
            this.groupEditVisible.set(true);
        }
    }

    /** Opens the same dialog with a blank draft to create a new exercise group. */
    openCreateGroupModal(): void {
        this.groupEditTarget.set({ title: '', isMandatory: true });
        this.groupEditIsNew.set(true);
        this.groupEditVisible.set(true);
    }

    /** Persists the group-edit dialog's result (create or update, per {@link groupEditIsNew}) and updates the local list. */
    onGroupEditSaved(edited: ExerciseGroup): void {
        if (this.groupEditIsNew()) {
            // Only the exam id is read server-side; sending the loaded exam would ship every group and exercise with it.
            const exam = new Exam();
            exam.id = this.examId();
            const newGroup: ExerciseGroup = { title: edited.title, isMandatory: edited.isMandatory, exam };
            this.exerciseGroupService.create(this.courseId(), this.examId(), newGroup).subscribe({
                next: (res) => this.exerciseGroups.set([...(this.exerciseGroups() ?? []), res.body!]),
                error: (res: HttpErrorResponse) => {
                    if (!isErrorAlert(res)) {
                        onError(this.alertService, res);
                    }
                },
            });
            return;
        }
        // Only these three fields are read server-side (ExerciseGroupUpdateDTO); `edited` still carries the group's
        // whole exercise list, which would otherwise be shipped on every rename.
        const update: ExerciseGroup = { id: edited.id, title: edited.title, isMandatory: edited.isMandatory };
        this.exerciseGroupService.update(this.courseId(), this.examId(), update).subscribe({
            next: (res) => {
                const saved = res.body!;
                // The response carries the group without its exercises, so copy the two edited fields onto a clone of
                // the local group rather than replacing it wholesale.
                this.exerciseGroups.set(
                    (this.exerciseGroups() ?? []).map((group) => {
                        if (group.id !== saved.id) {
                            return group;
                        }
                        const updated = deepClone(group);
                        updated.title = saved.title;
                        updated.isMandatory = saved.isMandatory;
                        return updated;
                    }),
                );
            },
            error: (res: HttpErrorResponse) => {
                if (!isErrorAlert(res)) {
                    onError(this.alertService, res);
                }
            },
        });
    }

    /**
     * Move the exercise group up one position in the order
     * @param index of the exercise group in the exerciseGroups array
     */
    moveUp(index: number): void {
        this.move(index, -1);
    }

    /**
     * Move the exercise group down one position in the order
     * @param index of the exercise group in the exerciseGroups array
     */
    moveDown(index: number): void {
        this.move(index, 1);
    }

    private move(index: number, offset: -1 | 1): void {
        // Ignore further reorder actions while a save is in flight: concurrent PUTs could otherwise arrive at the
        // server out of order and an earlier order would overwrite a later one.
        if (this.orderSavePending()) {
            return;
        }
        const exerciseGroups = this.exerciseGroups();
        if (!exerciseGroups) {
            return;
        }
        const previousOrder = [...exerciseGroups];
        [exerciseGroups[index], exerciseGroups[index + offset]] = [exerciseGroups[index + offset], exerciseGroups[index]];
        // Rebuild the array reference so the signal notifies and the (zoneless) view re-renders.
        this.exerciseGroups.set([...exerciseGroups]);
        this.saveOrder(previousOrder);
    }

    private saveOrder(previousOrder: ExerciseGroup[]): void {
        this.orderSavePending.set(true);
        this.examManagementService.updateOrder(this.courseId(), this.examId(), this.exerciseGroups()!).subscribe({
            // The response has no body; the already-applied optimistic order is the persisted order.
            next: () => this.orderSavePending.set(false),
            error: () => {
                // The server rejected the order (e.g. a stale tab whose groups no longer match the exam), so the
                // optimistic swap must not stay visible.
                this.exerciseGroups.set(previousOrder);
                this.alertService.error('artemisApp.examManagement.exerciseGroup.orderCouldNotBeSaved');
                this.orderSavePending.set(false);
            },
        });
    }

    /**
     * Moves an exercise into a different exercise group, triggered by dragging its row into another group's table.
     * Rejected by the server once student exams have been generated for the exam.
     */
    onTableGroupChange(event: ExamTableGroupChange): void {
        const exerciseId = event.exercise.id;
        const targetGroupId = event.group.id;
        if (exerciseId === undefined || targetGroupId === undefined) {
            return;
        }
        this.exerciseGroupService.moveExerciseToGroup(this.courseId(), this.examId(), exerciseId, targetGroupId).subscribe({
            next: () => {
                const exerciseGroups = this.exerciseGroups();
                if (!exerciseGroups) {
                    return;
                }
                // Clone the whole list once, then move the exercise inside the clone: the signal only notifies on a new
                // reference, and cloning keeps the detached exercises (with their dayjs dates) intact.
                const updatedGroups = deepClone(exerciseGroups);
                const moved = updatedGroups.flatMap((group) => group.exercises ?? []).find((exercise) => exercise.id === exerciseId);
                const targetGroup = updatedGroups.find((group) => group.id === targetGroupId);
                if (!moved || !targetGroup) {
                    return;
                }
                for (const group of updatedGroups) {
                    group.exercises = (group.exercises ?? []).filter((exercise) => exercise.id !== exerciseId);
                }
                // Empty groups arrive without an `exercises` property (the DTO omits empty collections).
                targetGroup.exercises ??= [];
                targetGroup.exercises.push(moved);
                this.exerciseGroups.set(updatedGroups);
            },
            // Nothing is applied optimistically, so a rejected move leaves the groups untouched and only needs an alert.
            error: (res: HttpErrorResponse) => {
                if (!isErrorAlert(res)) {
                    onError(this.alertService, res);
                }
            },
        });
    }

    /**
     * Opens the import dialog for an import of the exercise groups of another exam
     */
    openExerciseGroupImportModal() {
        this.groupImportVisible.set(true);
    }

    /**
     * Closes the import dialog and shows the exercise groups of the exam after the import
     * @param exerciseGroups all exercise groups of the exam after the import
     */
    protected onExerciseGroupsImported(exerciseGroups: ExerciseGroup[]): void {
        this.groupImportVisible.set(false);
        this.exerciseGroups.set(exerciseGroups);
        this.alertService.success('artemisApp.examManagement.exerciseGroup.importSuccessful');
    }

    protected containsProgrammingExercise(exerciseGroup: ExerciseGroup): boolean {
        return (exerciseGroup.exercises ?? []).some((exercise) => exercise.type === ExerciseType.PROGRAMMING);
    }

    /**
     * Opens the delete-confirmation dialog for an exercise group, mirroring the course-side exercise-group card's delete button.
     * The actual deletion runs on confirm via {@link deleteExerciseGroup}.
     */
    protected confirmDeleteGroup(exerciseGroup: ExerciseGroup): void {
        if (exerciseGroup.id === undefined) {
            return;
        }
        this.groupToDelete.set(exerciseGroup);
        this.deleteGroupVisible.set(true);
    }
}
