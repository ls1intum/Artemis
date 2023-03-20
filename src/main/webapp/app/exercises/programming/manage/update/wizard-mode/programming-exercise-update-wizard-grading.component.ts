import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { GradingStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-grading',
    templateUrl: './programming-exercise-update-wizard-grading.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardGradingComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() gradingStepInputs: GradingStepInputs;
}
