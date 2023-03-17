import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-difficulty',
    templateUrl: './programming-exercise-update-wizard-difficulty.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardDifficultyComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;
}
