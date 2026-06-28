import { Component, computed, inject, input, output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';
import { Exercise, ExerciseType, ExerciseVariantGroupReference } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { CourseExerciseGroup } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/core/course/manage/exercises/exercise-variant-group.service';
import { ExerciseGroupEditModalComponent } from 'app/core/course/manage/exercises-experimental/group-edit-modal/exercise-group-edit-modal.component';
import { DialogTranslateHeaderComponent } from 'app/shared-ui/dynamic-dialog/dialog-translate-header.component';
import { AlertService } from 'app/foundation/service/alert.service';

/**
 * Opens the {@link ExerciseGroupEditModalComponent} for an exercise that belongs to a variant group. The exercise edit
 * forms render their timeline date pickers as read-only "locked-to-group" fields (see {@code FormDateTimePickerComponent}):
 * clicking one calls {@link openModal}, which opens the group-edit dialog through PrimeNG's {@link DialogService}.
 * Saving persists the group's timeline via {@link ExerciseVariantGroupService} and re-emits the exercise with the
 * group's (now shared) dates applied so the form reflects them without a reload.
 *
 * The dialog is opened imperatively (rather than via a declarative {@code <p-dialog [visible]>}) because the latter
 * mis-layered its overlay on the first open from inside the large exercise-update form: the backdrop stayed transparent
 * and the locked date-field overlays painted over the dialog. {@code DialogService} appends to the body and manages the
 * overlay z-index through PrimeNG's overlay service, which renders correctly on the first open.
 */
@Component({
    selector: 'jhi-exercise-group-timeline-lock',
    template: '',
})
export class ExerciseGroupTimelineLockComponent {
    readonly exercise = input.required<Exercise>();
    /** Owning course id; falls back to {@code exercise.course?.id} when not provided by the host form. */
    readonly courseId = input<number | undefined>(undefined);
    /** Emits the exercise with the group's timeline applied after a successful group save, so the form can refresh. */
    readonly exerciseChange = output<Exercise>();

    private readonly exerciseVariantGroupService = inject(ExerciseVariantGroupService);
    private readonly alertService = inject(AlertService);
    private readonly dialogService = inject(DialogService);

    /** True when the exercise belongs to a (persisted) variant group, i.e. its timeline is group-governed. */
    readonly locked = computed(() => this.exercise()?.exerciseVariantGroup?.id !== undefined);

    /** The group-edit modal's model, derived from the embedded reference (member exercises are not needed to edit dates). */
    readonly group = computed<CourseExerciseGroup>(() => referenceToGroup(this.exercise()?.exerciseVariantGroup));

    private resolvedCourseId(): number | undefined {
        return this.courseId() ?? this.exercise()?.course?.id;
    }

    openModal(): void {
        if (!this.locked()) {
            return;
        }
        const dialogRef = this.dialogService.open(ExerciseGroupEditModalComponent, {
            inputValues: { group: this.group() },
            width: '780px',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            // Reactive title that re-translates on a language switch (a plain `header` string would not); see DialogTranslateHeaderComponent.
            data: { headerKey: 'artemisApp.exerciseManagement.groupEdit.header' },
            templates: { header: DialogTranslateHeaderComponent },
        });
        // The modal closes with the updated group on save, or `undefined` on cancel/dismiss.
        dialogRef?.onClose.subscribe((updated?: CourseExerciseGroup) => {
            if (updated) {
                this.onSave(updated);
            }
        });
    }

    onSave(updated: CourseExerciseGroup): void {
        const courseId = this.resolvedCourseId();
        if (courseId === undefined || updated.id === undefined) {
            return;
        }
        this.exerciseVariantGroupService
            .updateGroup(courseId, {
                id: updated.id,
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
            .subscribe({
                next: (dto) => this.exerciseChange.emit(withGroupTimeline(this.exercise(), dto)),
                error: (error: HttpErrorResponse) => this.alertService.addErrorAlert(error.error?.title ?? error.message, error.error?.message, error.error?.params),
            });
    }
}

/** Maps the embedded {@link ExerciseVariantGroupReference} to the {@link CourseExerciseGroup} the modal edits. */
function referenceToGroup(reference: ExerciseVariantGroupReference | undefined): CourseExerciseGroup {
    return {
        id: reference?.id,
        title: reference?.title,
        maxPoints: reference?.maxPoints,
        releaseDate: reference?.releaseDate,
        startDate: reference?.startDate,
        dueDate: reference?.dueDate,
        assessmentDueDate: reference?.assessmentDueDate,
        exampleSolutionPublicationDate: reference?.exampleSolutionPublicationDate,
        buildAndTestStudentSubmissionsAfterDueDate: reference?.buildAndTestStudentSubmissionsAfterDueDate,
        exercises: [],
    };
}

/**
 * Returns a new exercise (preserving the prototype, so a fresh reference triggers the host's signal) with the saved
 * group's timeline applied — including unset dates, since a grouped exercise is fully governed by its group's timeline.
 */
function withGroupTimeline(exercise: Exercise, dto: ExerciseVariantGroupDTO): Exercise {
    const updated = Object.assign(Object.create(Object.getPrototypeOf(exercise)), exercise) as Exercise;
    updated.releaseDate = dto.releaseDate;
    updated.startDate = dto.startDate;
    updated.dueDate = dto.dueDate;
    updated.assessmentDueDate = dto.assessmentDueDate;
    updated.exampleSolutionPublicationDate = dto.exampleSolutionPublicationDate;
    if (updated.type === ExerciseType.PROGRAMMING) {
        (updated as ProgrammingExercise).buildAndTestStudentSubmissionsAfterDueDate = dto.buildAndTestStudentSubmissionsAfterDueDate;
    }
    updated.exerciseVariantGroup = {
        id: dto.id,
        title: dto.title,
        maxPoints: dto.maxPoints,
        releaseDate: dto.releaseDate,
        startDate: dto.startDate,
        dueDate: dto.dueDate,
        assessmentDueDate: dto.assessmentDueDate,
        exampleSolutionPublicationDate: dto.exampleSolutionPublicationDate,
        buildAndTestStudentSubmissionsAfterDueDate: dto.buildAndTestStudentSubmissionsAfterDueDate,
    };
    return updated;
}
