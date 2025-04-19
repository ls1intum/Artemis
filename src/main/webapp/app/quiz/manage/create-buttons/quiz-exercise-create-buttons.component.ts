import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CreateQuizButtonComponent } from 'app/quiz/manage/create-buttons/create-button/create-quiz-button.component';
import { ImportQuizButtonComponent } from 'app/quiz/manage/create-buttons/import-button/import-quiz-button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faCheckDouble, faFileExport } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-quiz-exercise-create-buttons',
    templateUrl: './quiz-exercise-create-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective, CreateQuizButtonComponent, ImportQuizButtonComponent],
})
export class QuizExerciseCreateButtonsComponent {
    course = input<Course | undefined>();
    quizExercisesCount = input<number>(0);

    protected readonly faFileExport = faFileExport;
    protected readonly faCheckDouble = faCheckDouble;
}
