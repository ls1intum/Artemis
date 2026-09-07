import { Component, input, output } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * Stub for {@code ExerciseGroupTimelineLockComponent}. Update-form templates call {@code locked()} /
 * {@code openModal()} through a template variable, which a plain ng-mocks mock does not expose.
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
