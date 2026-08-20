import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Exercise, ExerciseVariantGroupReference } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService, isPersistableGroup, toUpdateGroupPayload } from 'app/course/manage/exercises/exercise-variant-group.service';
import { ExerciseGroupEditModalComponent } from 'app/course/manage/exercises/group-edit-modal/exercise-group-edit-modal.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';

/**
 * Owns the group timeline edit modal for an exercise update form. The host shows an explicit edit control and calls
 * {@link openModal}. Saving persists the group timeline and re-emits the exercise with the shared dates applied.
 */
@Component({
    selector: 'jhi-exercise-group-timeline-lock',
    template: `@if (locked()) {
        <jhi-exercise-group-edit-modal [(visible)]="showModal" [group]="group()" (saved)="onSave($event)" />
    }`,
    imports: [ExerciseGroupEditModalComponent],
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

    /** Visibility of the declarative group-edit modal rendered in this component's template. */
    readonly showModal = signal(false);

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
        this.showModal.set(true);
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
 * Returns a new exercise (preserving the prototype, so a fresh reference triggers the host's signal) with the saved group's
 * timeline applied, including unset dates, since a grouped exercise is fully governed by its group.
 */
function withGroupTimeline(exercise: Exercise, dto: ExerciseVariantGroupDTO): Exercise {
    // deepClone keeps the prototype, so the fresh reference still satisfies the host's signal comparison
    const updated = deepClone(exercise);
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
