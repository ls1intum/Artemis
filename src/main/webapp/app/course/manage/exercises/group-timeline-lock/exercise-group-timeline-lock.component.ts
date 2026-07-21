import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DialogService } from 'primeng/dynamicdialog';
import { Exercise, ExerciseVariantGroupReference } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService, isPersistableGroup, toUpdateGroupPayload } from 'app/course/manage/exercises/exercise-variant-group.service';
import { ExerciseGroupEditModalComponent } from 'app/course/manage/exercises/group-edit-modal/exercise-group-edit-modal.component';
import { DialogTranslateHeaderComponent } from 'app/shared-ui/dynamic-dialog/dialog-translate-header.component';
import { AlertService } from 'app/foundation/service/alert.service';

/**
 * Opens the {@link ExerciseGroupEditModalComponent} for an exercise that belongs to a variant group. The exercise edit
 * forms render their timeline date pickers as read-only "locked-to-group" fields (see {@code FormDateTimePickerComponent}):
 * clicking one calls {@link openModal}, which opens the group-edit dialog through PrimeNG's {@link DialogService}.
 * Saving persists the group's timeline via {@link ExerciseVariantGroupService} and re-emits the exercise with the
 * group's (now shared) dates applied so the form reflects them without a reload.
 */
@Component({
    selector: 'jhi-exercise-group-timeline-lock',
    template: '',
    changeDetection: ChangeDetectionStrategy.OnPush,
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
        const groupId = updated.id;
        // The modal's Save button already enforces a non-empty title; narrowing here makes that guarantee explicit.
        if (courseId === undefined || groupId === undefined || !isPersistableGroup(updated)) {
            return;
        }
        this.exerciseVariantGroupService.updateGroup(courseId, toUpdateGroupPayload(updated, groupId)).subscribe({
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
    // The build-and-test date is not part of the shared timeline: the server re-derives it per programming exercise
    // from the new due date, so it is left untouched here.
    updated.exerciseVariantGroup = {
        id: dto.id,
        title: dto.title,
        maxPoints: dto.maxPoints,
        releaseDate: dto.releaseDate,
        startDate: dto.startDate,
        dueDate: dto.dueDate,
        assessmentDueDate: dto.assessmentDueDate,
        exampleSolutionPublicationDate: dto.exampleSolutionPublicationDate,
    };
    return updated;
}
