import { Component, Input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { LanguageStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-language',
    templateUrl: './programming-exercise-update-wizard-language.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardLanguageComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() languageStepInputs: LanguageStepInputs;
}
