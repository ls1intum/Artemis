import { Component } from '@angular/core';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-additional-settings',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepTitle">Additional settings</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.difficultyStepMessage">Set additional settings related to infrastructure etc</span></p>
        <jhi-programming-exercise-additional-settings></jhi-programming-exercise-additional-settings>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardAdditionalSettingsComponent {}
