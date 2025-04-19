import { Component, input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';

@Component({
    selector: 'jhi-exercise-create-buttons',
    templateUrl: './exercise-create-buttons.component.html',
    imports: [RouterLink, FaIconComponent, ExerciseCreateButtonComponent, ExerciseImportButtonComponent],
})
export class ExerciseCreateButtonsComponent {
    course = input<Course | undefined>();
    exerciseType = input<ExerciseType>();
}
