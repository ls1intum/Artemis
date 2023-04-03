import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-problem',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.problemStepTitle">Problem</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.problemStepMessage">Set problem.</span></p>
        <div class="form-group">
            <jhi-programming-exercise-problem [(exercise)]="exercise"></jhi-programming-exercise-problem>
        </div>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardProblemComponent {
    programmingExercise: ProgrammingExercise;

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
