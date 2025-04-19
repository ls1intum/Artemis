import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
    faChalkboardUser,
    faChartBar,
    faClipboard,
    faFileImport,
    faGraduationCap,
    faKeyboard,
    faListAlt,
    faPause,
    faPlus,
    faQuestion,
    faTimes,
} from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CreateProgrammingButtonComponent } from 'app/programming/manage/create-buttons/create-button/create-programming-button.component';
import { ImportProgrammingButtonComponent } from 'app/programming/manage/create-buttons/import-button/import-programming-button.component';
import { CreateQuizButtonComponent } from 'app/quiz/manage/create-buttons/create-button/create-quiz-button.component';
import { ImportQuizButtonComponent } from 'app/quiz/manage/create-buttons/import-button/import-quiz-button.component';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';

@Component({
    selector: 'jhi-add-exercise-modal',
    imports: [
        TranslateDirective,
        ButtonComponent,
        CreateProgrammingButtonComponent,
        ImportProgrammingButtonComponent,
        CreateQuizButtonComponent,
        ImportQuizButtonComponent,
        ExerciseCreateButtonComponent,
        ExerciseImportButtonComponent,
    ],
    templateUrl: './add-exercise-modal.component.html',
})
export class AddExerciseModalComponent {
    private activeModal = inject(NgbActiveModal);
    private router = inject(Router);
    course: Course;

    protected readonly faTimes = faTimes;
    protected readonly faPause = faPause;
    protected readonly faListAlt = faListAlt;
    protected readonly faChartBar = faChartBar;
    protected readonly faClipboard = faClipboard;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faChalkboardUser = faChalkboardUser;
    protected readonly faKeyboard = faKeyboard;
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;
    protected readonly faQuestion = faQuestion;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.activeModal.dismiss('cancel');
    }

    confirm() {
        this.activeModal.close(true);
    }
    linkToProgrammingExerciseCreation() {
        if (!this.course.id) {
            return;
        }
        this.activeModal.close(true);
        this.router.navigate(['course-management', this.course.id, 'programming-exercises', 'new']);
    }

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ExerciseType = ExerciseType;
}
