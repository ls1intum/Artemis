import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-info',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.generalInfoStepTitle">General Info</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.generalInfoStepMessage">Add general info.</span></p>
        <jhi-programming-exercise-info [isExamMode]="false" [programmingExercise]="programmingExercise"> </jhi-programming-exercise-info>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardInformationComponent {
    @Input() programmingExercise: ProgrammingExercise;
}
