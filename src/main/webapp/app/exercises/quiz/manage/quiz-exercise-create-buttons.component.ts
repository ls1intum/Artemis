import { Component, Input, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { faCheckDouble, faFileExport, faFileImport, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Component({
    selector: 'jhi-quiz-exercise-create-buttons',
    templateUrl: './quiz-exercise-create-buttons.component.html',
})
export class QuizExerciseCreateButtonsComponent {
    private router = inject(Router);
    private modalService = inject(NgbModal);

    @Input() course: Course;

    @Input() quizExercisesCount: number;

    faPlus = faPlus;
    faFileImport = faFileImport;
    faFileExport = faFileExport;
    faCheckDouble = faCheckDouble;

    /**
     * Opens the import modal for a quiz exercise
     */
    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = ExerciseType.QUIZ;
        modalRef.result.then((result: QuizExercise) => {
            this.router.navigate(['course-management', this.course.id, 'quiz-exercises', result.id, 'import']);
        });
    }
}
