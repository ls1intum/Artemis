import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-grading',
    templateUrl: './programming-exercise-update-wizard-grading.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardGradingComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;
}
