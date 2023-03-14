import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { InfoStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-info',
    templateUrl: './programming-exercise-information.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseInformationComponent {
    @Input() isImport: boolean;
    @Input() isExamMode: boolean;
    @Input() isEdit: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() shouldHidePreview = false;
    @Input() infoInputs: InfoStepInputs;
}
