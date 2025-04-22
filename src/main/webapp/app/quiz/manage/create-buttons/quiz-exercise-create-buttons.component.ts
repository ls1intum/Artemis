import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faCheckDouble, faFileExport } from '@fortawesome/free-solid-svg-icons';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';

@Component({
    selector: 'jhi-quiz-exercise-create-buttons',
    templateUrl: './quiz-exercise-create-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, ExerciseCreateButtonComponent, ExerciseImportButtonComponent],
})
export class QuizExerciseCreateButtonsComponent {
    course = input.required<Course>();
    quizExercisesCount = input<number>(0);

    protected readonly faFileExport = faFileExport;
    protected readonly faCheckDouble = faCheckDouble;
    protected readonly ExerciseType = ExerciseType;
}
