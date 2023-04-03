import { Component } from '@angular/core';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-language',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.languageStepTitle">Language</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.languageStepMessage">Set language.</span></p>
        <jhi-programming-exercise-language></jhi-programming-exercise-language>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardLanguageComponent {}
