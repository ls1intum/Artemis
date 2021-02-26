import { Component, Input } from '@angular/core';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

@Component({
    selector: 'jhi-exam-checklist-exercisegroup-table',
    templateUrl: './exam-checklist-exercisegroup-table.component.html',
})
export class ExamChecklistExerciseGroupTableComponent {
    @Input() exerciseGroups: ExerciseGroup[];
}
