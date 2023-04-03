import { Component, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseUpdateService } from 'app/exercises/programming/manage/update/programming-exercise-update.service';

@Component({
    selector: 'jhi-programming-exercise-language',
    templateUrl: './programming-exercise-language.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseLanguageComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;

    @Input() sequentialTestRunsAllowed: boolean;

    faQuestionCircle = faQuestionCircle;

    constructor(public programmingExerciseUpdateService: ProgrammingExerciseUpdateService) {}
}
