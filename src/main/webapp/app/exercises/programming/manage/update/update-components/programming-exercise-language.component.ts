import { Component, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { GradingStepInputs, LanguageStepInputs } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-language',
    templateUrl: './programming-exercise-language.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseLanguageComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;
    @Input() languageStepInputs: LanguageStepInputs;
    @Input() gradingInputs: GradingStepInputs;
    @Input() sequentialTestRunsAllowed: boolean;

    faQuestionCircle = faQuestionCircle;
}
