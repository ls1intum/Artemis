import { Component, Input } from '@angular/core';
import { InfrastructureStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-infrastructure',
    templateUrl: './programming-exercise-update-wizard-infrastructure.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardInfrastructureComponent {
    @Input() infrastructureStepInputs: InfrastructureStepInputs;
    @Input() programmingExercise: ProgrammingExercise;
}
