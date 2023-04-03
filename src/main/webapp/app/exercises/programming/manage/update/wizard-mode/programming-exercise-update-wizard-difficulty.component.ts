import { Component } from '@angular/core';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-difficulty',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepTitle">Difficulty</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepMessage">Set difficulty.</span></p>
        <jhi-programming-exercise-difficulty></jhi-programming-exercise-difficulty>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardDifficultyComponent {}
