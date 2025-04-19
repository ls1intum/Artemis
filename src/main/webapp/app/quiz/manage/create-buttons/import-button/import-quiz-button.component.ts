import { Component, inject, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faCheckDouble, faFileImport } from '@fortawesome/free-solid-svg-icons';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-quiz-import-button',
    imports: [TranslateDirective, FaIconComponent],
    templateUrl: './import-quiz-button.component.html',
})
export class ImportQuizButtonComponent {
    private router = inject(Router);
    private modalService = inject(NgbModal);

    course = input<Course | undefined>();
    translationKey = input<string>('artemisApp.quizExercise.home.importLabel');

    protected readonly faFileImport = faFileImport;
    protected readonly faCheckDouble = faCheckDouble;

    /**
     * Opens the import modal for a quiz exercise
     */
    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.QUIZ;
        modalRef.result.then((result: QuizExercise) => {
            this.modalService.dismissAll();
            this.router.navigate(['/course-management', this.course()?.id, 'quiz-exercises', result.id, 'import']);
        });
    }
}
