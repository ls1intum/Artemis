import { Component, Input } from '@angular/core';
import { InfrastructureInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-infrastructure',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.generalInfoStepTitle">General Info</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.generalInfoStepMessage">Add general info.</span></p>
        <jhi-programming-exercise-infrastructure [infrastructureStepInputs]="infrastructureInputs"> </jhi-programming-exercise-infrastructure>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardInfrastructureComponent {
    @Input() infrastructureInputs: InfrastructureInputs;
}
