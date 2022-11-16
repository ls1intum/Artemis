import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { InfoStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-info',
    templateUrl: './programming-exercise-update-wizard-information.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardInformationComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() infoStepInputs: InfoStepInputs;
}
