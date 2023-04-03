import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-grading',
    template: `
        <h1><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.gradingStepTitle">Grading</span></h1>
        <p><span jhiTranslate="artemisApp.programmingExercise.wizardMode.detailedSteps.gradingStepMessage">Set grading.</span></p>
        <jhi-programming-exercise-grading class="form-step" [programmingExercise]="programmingExercise" [showSummary]="true"></jhi-programming-exercise-grading>
    `,
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardGradingComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;
}
