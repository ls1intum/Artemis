import { Component, Input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-language',
    templateUrl: './programming-exercise-language.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseLanguageComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() isImportFromExistingExercise: boolean;
    @Input() isImportFromFile: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    faQuestionCircle = faQuestionCircle;
}
