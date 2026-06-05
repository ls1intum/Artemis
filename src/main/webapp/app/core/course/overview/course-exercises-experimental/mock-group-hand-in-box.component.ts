import { Component, computed, inject, input, isDevMode } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleCheck, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { GroupHandInSelectionService } from './group-hand-in-selection.service';

/**
 * Dev-only mock info box for the exercise detail header: when the opened exercise belongs to a mock
 * exercise group, it shows whether the exercise has been selected (handed in) for that group's score.
 * Renders nothing for real exercises or outside dev mode, so it is inert when embedded in the shared
 * header. Matches the header's information-box style (border + fit-content, only as wide as its text).
 */
@Component({
    selector: 'jhi-mock-group-hand-in-box',
    imports: [FaIconComponent],
    template: `
        @if (selected() !== undefined) {
            <div class="mock-hand-in-box text-nowrap rounded-3 py-1 px-2 border border-1 small fw-semibold h-100" [class.border-warning]="!selected()">
                <div class="text-body-tertiary">Hand-in</div>
                <div [class.text-success]="selected()" [class.text-warning]="!selected()">
                    <fa-icon [icon]="selected() ? faCircleCheck : faTriangleExclamation" />
                    {{ selected() ? 'Selected' : 'Not selected' }}
                </div>
            </div>
        }
    `,
    styles: `
        .mock-hand-in-box {
            max-width: fit-content;
            background-color: var(--module-bg);
        }
    `,
})
export class MockGroupHandInBoxComponent {
    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly selectionService = inject(GroupHandInSelectionService);

    readonly exerciseId = input<number | undefined>(undefined);

    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    /** undefined = not a mock group exercise (renders nothing); true/false = selected for hand-in or not. */
    protected readonly selected = computed<boolean | undefined>(() => {
        if (!isDevMode()) {
            return undefined;
        }
        const id = this.exerciseId();
        if (id === undefined) {
            return undefined;
        }
        const group = this.mockService.getGroups().find((candidate) => (candidate.exercises ?? []).some((exercise) => exercise.id === id));
        if (!group || group.id === undefined) {
            return undefined;
        }
        return this.selectionService.getSubmittedSelection(group.id).includes(id);
    });
}
