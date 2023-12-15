import { Component, Input, OnInit } from '@angular/core';
import { DEFAULT_PLAGIARISM_DETECTION_CONFIG, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exercise-update-plagiarism',
    templateUrl: './exercise-update-plagiarism.component.html',
})
export class ExerciseUpdatePlagiarismComponent implements OnInit {
    @Input() exercise: Exercise;
    minimumSizeTooltip?: string;

    readonly faQuestionCircle = faQuestionCircle;

    ngOnInit(): void {
        this.minimumSizeTooltip = this.getMinimumSizeTooltip();
        if (!this.exercise.plagiarismDetectionConfig) {
            // Create the default plagiarism configuration if there is none (e.g. importing an old exercise from a file)
            this.exercise.plagiarismDetectionConfig = DEFAULT_PLAGIARISM_DETECTION_CONFIG;
        }
    }

    toggleCPCEnabled() {
        const config = this.exercise.plagiarismDetectionConfig!;
        const newValue = !config.continuousPlagiarismControlEnabled;
        config.continuousPlagiarismControlEnabled = newValue;
        config.continuousPlagiarismControlPostDueDateChecksEnabled = newValue;
    }

    /**
     * Return the translation identifier of the minimum size tooltip for the current exercise type.
     */
    getMinimumSizeTooltip(): string | undefined {
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
