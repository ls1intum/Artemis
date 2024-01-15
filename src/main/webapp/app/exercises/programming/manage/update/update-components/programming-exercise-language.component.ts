import { Component, Input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { PROFILE_AEOLUS, PROFILE_LOCALCI } from 'app/app.constants';

@Component({
    selector: 'jhi-programming-exercise-language',
    templateUrl: './programming-exercise-language.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseLanguageComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    faQuestionCircle = faQuestionCircle;
    protected readonly PROFILE_LOCALCI = PROFILE_LOCALCI;
    protected readonly PROFILE_AEOLUS = PROFILE_AEOLUS;
}
