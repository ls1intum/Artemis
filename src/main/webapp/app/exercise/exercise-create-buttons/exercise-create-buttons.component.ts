import { Component, input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-exercise-create-buttons',
    templateUrl: './exercise-create-buttons.component.html',
    imports: [ExerciseCreateButtonComponent, ExerciseImportButtonComponent],
})
export class ExerciseCreateButtonsComponent {
    course = input.required<Course>();
    exerciseType = input.required<ExerciseType>();
    featureToggle = input<FeatureToggle | undefined>();
}
