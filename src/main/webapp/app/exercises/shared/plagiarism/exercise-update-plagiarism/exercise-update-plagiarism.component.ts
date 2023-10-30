import { Component, Input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exercise-update-plagiarism',
    templateUrl: './exercise-update-plagiarism.component.html',
})
export class ExerciseUpdatePlagiarismComponent {
    @Input() exercise: Exercise;

    faQuestionCircle = faQuestionCircle;

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip() {
        switch (this.exercise.type) {
            case ExerciseType.PROGRAMMING: {
                return 'artemisApp.plagiarism.minimumSizeTooltipProgrammingExercise';
            }
            case ExerciseType.TEXT: {
                return 'artemisApp.plagiarism.minimumSizeTooltipTextExercise';
            }
            case ExerciseType.MODELING: {
                return 'artemisApp.plagiarism.minimumSizeTooltipModelingExercise';
            }
        }
    }
}
