import { Component, input, output } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * Stub for {@code ExerciseGroupTimelineLockComponent}. The update-form templates reference it through a template
 * variable (e.g. {@code #variantLock}) and call {@code variantLock.locked()} / {@code variantLock.openModal()} on the
 * timeline date pickers, so the stub must expose those members (a plain ng-mocks mock only provides inputs/outputs).
 */
@Component({
    selector: 'jhi-exercise-group-timeline-lock',
    template: '',
})
export class ExerciseGroupTimelineLockStubComponent {
    exercise = input.required<Exercise>();
    courseId = input<number | undefined>(undefined);
    exerciseChange = output<Exercise>();

    locked = () => false;
    openModal = (): void => {};
}
