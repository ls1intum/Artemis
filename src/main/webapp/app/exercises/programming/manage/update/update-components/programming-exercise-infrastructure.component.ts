import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { InfrastructureInputs } from '../wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-infrastructure',
    templateUrl: './programming-exercise-infrastructure.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseInfrastructureComponent {
    @Input() isImportFromExistingExercise: boolean;
    @Input() isEdit: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() shouldHidePreview = false;
    @Input() infrastructureInputs: InfrastructureInputs;
    @Input() auxiliaryRepositoriesSupported = false;
}
