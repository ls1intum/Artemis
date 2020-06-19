import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-plans-and-repositories-preview',
    templateUrl: './programming-exercise-plans-and-repositories-preview.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExercisePlansAndRepositoriesPreviewComponent {
    @Input() programmingExercise: ProgrammingExercise | null;

    getCourseShortName() {
        if (this.programmingExercise?.course) {
            return this.programmingExercise?.course?.shortName;
        } else if (this.programmingExercise?.exerciseGroup) {
            return this.programmingExercise?.exerciseGroup?.exam?.course?.shortName;
        }
    }
}
