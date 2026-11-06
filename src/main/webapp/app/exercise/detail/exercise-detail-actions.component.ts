import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Keeps navigation and editing on two deliberate rows, with secondary actions beside editing. */
@Component({
    selector: 'jhi-exercise-detail-actions',
    templateUrl: './exercise-detail-actions.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseDetailActionsComponent {}
