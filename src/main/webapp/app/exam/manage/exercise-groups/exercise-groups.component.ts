import { TumUiButtonComponent, TumUiPanelComponent } from '@tumaet/ui-angular';
import { Component, OnInit, Type, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/foundation/util/global.utils';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { faAngleDown, faAngleUp, faFileImport, faLayerGroup, faPen, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ExamImportComponent, ExamImportDialogData } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ExerciseImportComponent, ExerciseImportDialogData } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_FILEUPLOAD, MODULE_FEATURE_MODELING, MODULE_FEATURE_TEXT, PROFILE_LOCALCI } from 'app/app.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { ExamExerciseTableComponent, ExamTableGroupChange } from 'app/exam/manage/exercise-groups/exercise-table/exam-exercise-table.component';
import { ExamExerciseGroupEditModalComponent } from 'app/exam/manage/exercise-groups/group-edit-modal/exam-exercise-group-edit-modal.component';
import { ExamExerciseTypePickerComponent, ExamExerciseTypePickerMode } from 'app/exam/manage/exercise-groups/exercise-type-picker/exam-exercise-type-picker.component';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-exercise-groups',
    templateUrl: './exercise-groups.component.html',
    styleUrls: ['./exercise-groups.component.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        ArtemisTranslatePipe,
        TumUiPanelComponent,
        TumUiButtonComponent,
        ExamExerciseTableComponent,
        ExamExerciseGroupEditModalComponent,
        ExamExerciseTypePickerComponent,
    ],
})
export class ExerciseGroupsComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseGroupService = inject(ExerciseGroupService);
    private examManagementService = inject(ExamManagementService);
    private eventManager = inject(EventManager);
    private alertService = inject(AlertService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private router = inject(Router);
    private profileService = inject(ProfileService);
    private deleteDialogService = inject(DeleteDialogService);

    readonly courseId = signal<number>(undefined!);
    course = signal<Course | undefined>(undefined);
    readonly examId = signal<number>(undefined!);
    exam = signal<Exam | undefined>(undefined);
    exerciseGroups = signal<ExerciseGroup[] | undefined>(undefined);
    dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();
    latestIndividualEndDate = signal<dayjs.Dayjs | undefined>(undefined);

    localCIEnabled = signal(true);
    disabledExerciseTypes: ExerciseType[] = [];

    /** Ids (as strings) of every group's drop list, so exercises can be dragged between any two group tables. */
    dropListIds(): string[] {
        return (this.exerciseGroups() ?? []).map((group) => this.groupDropListId(group.id));
    }

    groupDropListId(groupId: number | undefined): string {
        return `exercise-group-${groupId}`;
    }

    readonly typePickerVisible = signal(false);
    readonly typePickerGroupId = signal<number | undefined>(undefined);
    readonly typePickerMode = signal<ExamExerciseTypePickerMode>('create');

    readonly groupEditVisible = signal(false);
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
        if (exerciseGroups) {
            exerciseGroups.forEach((exerciseGroup) => {
                if (exerciseGroup.id === exerciseGroupId && exerciseGroup.exercises && exerciseGroup.exercises.length > 0) {
                    exerciseGroup.exercises = exerciseGroup.exercises.filter((exercise) => exercise.id !== exerciseId);
                    // Rebuild the array reference so the signal notifies and the (zoneless) view re-renders.
                    this.exerciseGroups.set([...exerciseGroups]);
                }
            });
        }
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
     * Opens the import module for a specific exercise type
     * @param exerciseGroup The current exercise group
     * @param exerciseType The exercise type you want to import
     */
    openImportModal(exerciseGroup: ExerciseGroup, exerciseType: ExerciseType) {
        const importBaseRoute = ['/course-management', this.courseId(), 'exams', this.examId(), 'exercise-groups', exerciseGroup.id, `${exerciseType}-exercises`];
        const dialogData: ExerciseImportDialogData = { exerciseType };

        // Determine the header key based on exercise type
        const headerKey = exerciseType === ExerciseType.FILE_UPLOAD ? 'artemisApp.fileUploadExercise.home.importLabel' : `artemisApp.${exerciseType}Exercise.home.importLabel`;

        // For programming exercises, use tabs component (allows import from file), otherwise use direct import
        const componentToOpen: Type<ExerciseImportTabsComponent | ExerciseImportComponent> =
            exerciseType === ExerciseType.PROGRAMMING ? ExerciseImportTabsComponent : ExerciseImportComponent;

        const dialogRef = this.dialogService.open(componentToOpen, {
            header: this.translateService.instant(headerKey),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: dialogData,
        });

        dialogRef?.onClose.subscribe((result: Exercise | undefined) => {
            if (result) {
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
        });
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
            const newGroup: ExerciseGroup = { ...edited, exam: this.exam() };
            this.exerciseGroupService.create(this.courseId(), this.examId(), newGroup).subscribe({
                next: (res) => this.exerciseGroups.set([...(this.exerciseGroups() ?? []), res.body!]),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
            return;
        }
        this.exerciseGroupService.update(this.courseId(), this.examId(), edited).subscribe({
            next: (res) => {
                const saved = res.body!;
                this.exerciseGroups.set((this.exerciseGroups() ?? []).map((g) => (g.id === saved.id ? { ...g, ...saved } : g)));
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Move the exercise group up one position in the order
     * @param index of the exercise group in the exerciseGroups array
     */
    moveUp(index: number): void {
        const exerciseGroups = this.exerciseGroups();
        if (exerciseGroups) {
            [exerciseGroups[index], exerciseGroups[index - 1]] = [exerciseGroups[index - 1], exerciseGroups[index]];
            this.exerciseGroups.set([...exerciseGroups]);
        }
        this.saveOrder();
    }

    /**
     * Move the exercise group down one position in the order
     * @param index of the exercise group in the exerciseGroups array
     */
    moveDown(index: number): void {
        const exerciseGroups = this.exerciseGroups();
        if (exerciseGroups) {
            [exerciseGroups[index], exerciseGroups[index + 1]] = [exerciseGroups[index + 1], exerciseGroups[index]];
            this.exerciseGroups.set([...exerciseGroups]);
        }
        this.saveOrder();
    }

    private saveOrder(): void {
        this.examManagementService.updateOrder(this.courseId(), this.examId(), this.exerciseGroups()!).subscribe({
            next: (res) => this.exerciseGroups.set(res.body!),
            error: () => this.alertService.error('artemisApp.examManagement.exerciseGroup.orderCouldNotBeSaved'),
        });
    }

    /**
     * Moves an exercise into a different exercise group, triggered by the table's drag-and-drop or group dropdown.
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
                const moved = exerciseGroups.flatMap((g) => g.exercises ?? []).find((e) => e.id === exerciseId);
                if (!moved) {
                    return;
                }
                this.exerciseGroups.set(
                    exerciseGroups.map((g) => {
                        if (g.id === targetGroupId) {
                            return { ...g, exercises: [...(g.exercises ?? []), moved] };
                        }
                        return { ...g, exercises: (g.exercises ?? []).filter((e) => e.id !== exerciseId) };
                    }),
                );
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Opens the import module for an exam import
     */
    openExerciseGroupImportModal() {
        const dialogData: ExamImportDialogData = {
            subsequentExerciseGroupSelection: true,
            targetCourseId: this.courseId(),
            targetExamId: this.examId(),
        };

        const dialogRef = this.dialogService.open(ExamImportComponent, {
            header: this.translateService.instant('artemisApp.examManagement.importExam'),
            width: '70rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            draggable: false,
            data: dialogData,
        });

        dialogRef?.onClose.subscribe((exerciseGroups: ExerciseGroup[] | undefined) => {
            if (exerciseGroups) {
                this.exerciseGroups.set(exerciseGroups);
                this.alertService.success('artemisApp.examManagement.exerciseGroup.importSuccessful');
            }
        });
    }

    protected containsProgrammingExercise(exerciseGroup: ExerciseGroup): boolean {
        return (exerciseGroup.exercises ?? []).some((exercise) => exercise.type === ExerciseType.PROGRAMMING);
    }

    /**
     * Opens the shared delete-confirmation dialog for an exercise group, mirroring the course-side exercise-group
     * card's delete button. A group containing a programming exercise gets the LocalVC-aware question plus the
     * build-plan cleanup checks (unless LocalCI is active, which needs none); every other group gets the plain
     * question. The actual deletion runs on confirm via {@link deleteExerciseGroup}.
     */
    protected confirmDeleteGroup(exerciseGroup: ExerciseGroup): void {
        const groupId = exerciseGroup.id;
        if (groupId === undefined) {
            return;
        }
        const isProgrammingGroup = this.containsProgrammingExercise(exerciseGroup);
        this.deleteDialogService.openDeleteDialog({
            entityTitle: exerciseGroup.title,
            deleteQuestion: isProgrammingGroup ? 'artemisApp.examManagement.exerciseGroup.delete.questionLocalVC' : 'artemisApp.examManagement.exerciseGroup.delete.question',
            deleteConfirmationText: 'artemisApp.examManagement.exerciseGroup.delete.typeNameToConfirm',
            translateValues: {},
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            requireConfirmationOnlyForAdditionalChecks: false,
            additionalChecks:
                isProgrammingGroup && !this.localCIEnabled()
                    ? {
                          deleteStudentReposBuildPlans: 'artemisApp.programmingExercise.delete.studentReposBuildPlans',
                          deleteBaseReposBuildPlans: 'artemisApp.programmingExercise.delete.baseReposBuildPlans',
                      }
                    : {},
            dialogError: this.dialogError,
            delete: (checks) => this.deleteExerciseGroup(groupId, checks),
        });
    }
}
