import { Component, input, viewChild } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Popover } from 'primeng/popover';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

interface ExerciseModalRow {
    type: ExerciseType;
    translationKey: string;
    featureToggle?: FeatureToggle;
}

@Component({
    selector: 'jhi-add-exercise-popover',
    imports: [TranslateDirective, ExerciseCreateButtonComponent, ExerciseImportButtonComponent, Popover, FaIconComponent],
    templateUrl: './add-exercise-popover.component.html',
    styleUrls: ['./add-exercise-popover.component.scss'],
    exportAs: 'addExercisePopover',
})
export class AddExercisePopoverComponent {
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ExerciseType = ExerciseType;
    protected readonly faTimes = faTimes;
    protected readonly exerciseTypes: ExerciseModalRow[] = [
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
    course = input.required<Course>();

    addExercisePopover = viewChild<Popover>('addExercisePopover');

    /**
     * Show the overlay panel
     * @param event The click event
     */
    public showPopover(event: Event): void {
        this.addExercisePopover()?.show(event);
    }

    /**
     * Hide the overlay panel
     */
    hidePopover(): void {
        this.addExercisePopover()?.hide();
    }
}
