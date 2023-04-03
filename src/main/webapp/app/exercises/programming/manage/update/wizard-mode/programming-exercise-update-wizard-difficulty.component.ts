import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-difficulty',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepTitle">Difficulty</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepMessage">Set difficulty.</span></p>
        <jhi-programming-exercise-difficulty [programmingExercise]="programmingExercise"></jhi-programming-exercise-difficulty>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardDifficultyComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;
}
