import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-info',
    templateUrl: './programming-exercise-update-wizard-information.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardInformationComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;
}
