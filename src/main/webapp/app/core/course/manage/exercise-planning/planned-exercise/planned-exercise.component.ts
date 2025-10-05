import { Component, inject, input, output } from '@angular/core';
import { TableModule } from 'primeng/table';
import { PlannedExerciseService } from 'app/core/course/shared/services/planned-exercise.service';
import { PlannedExercise } from 'app/core/course/shared/entities/planned-exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGhost, faPenToSquare, faTrash } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-planned-exercise',
    imports: [TableModule, FaIconComponent],
    templateUrl: './planned-exercise.component.html',
    styleUrl: './planned-exercise.component.scss',
})
export class PlannedExerciseComponent {
    private plannedExerciseService = inject(PlannedExerciseService);
    protected readonly faTrash = faTrash;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faGhost = faGhost;

    courseId = input.required<number>();
    isAtLeastInstructor = input.required<boolean>();
    plannedExercises = input.required<PlannedExercise[]>();
    onSelectPlannedExerciseToEdit = output<PlannedExercise>();

    edit(plannedExercise: PlannedExercise) {
        this.onSelectPlannedExerciseToEdit.emit(plannedExercise);
    }

    delete(plannedExercise: PlannedExercise) {
        this.plannedExerciseService.delete(plannedExercise.id, this.courseId());
    }
}
