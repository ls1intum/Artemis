import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProblemStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-problem',
    templateUrl: './programming-exercise-update-wizard-problem.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardProblemComponent {
    programmingExercise: ProgrammingExercise;

    @Input() isImport: boolean;
    @Input() problemStepInputs: ProblemStepInputs;

    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();

    @Input()
    get exercise() {
        return this.programmingExercise;
    }

    set exercise(exercise: ProgrammingExercise) {
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }
}
