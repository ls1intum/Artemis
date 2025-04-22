import { Component, inject } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/button/button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';

interface ExerciseModalRow {
    type: ExerciseType;
    translationKey: string;
    featureToggle?: FeatureToggle;
}

@Component({
    selector: 'jhi-add-exercise-modal',
    imports: [TranslateDirective, ExerciseCreateButtonComponent, ExerciseImportButtonComponent],
    templateUrl: './add-exercise-modal.component.html',
})
export class AddExerciseModalComponent {
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ExerciseType = ExerciseType;
    private activeModal = inject(NgbActiveModal);
    course: Course;

    /**
     * Closes the modal by dismissing it
     */
    cancel() {
        this.activeModal.dismiss('cancel');
    }

    exerciseTypes: ExerciseModalRow[] = [
        {
            type: ExerciseType.PROGRAMMING,
            translationKey: 'global.menu.entities.exerciseTypes.programming',
            featureToggle: FeatureToggle.ProgrammingExercises,
        },
        {
            type: ExerciseType.QUIZ,
            translationKey: 'global.menu.entities.exerciseTypes.quiz',
        },
        {
            type: ExerciseType.MODELING,
            translationKey: 'global.menu.entities.exerciseTypes.modeling',
        },
        {
            type: ExerciseType.TEXT,
            translationKey: 'global.menu.entities.exerciseTypes.text',
        },
        {
            type: ExerciseType.FILE_UPLOAD,
            translationKey: 'global.menu.entities.exerciseTypes.fileUpload',
        },
    ];
}
