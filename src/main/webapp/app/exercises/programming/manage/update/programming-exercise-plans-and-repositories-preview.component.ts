import { Component, Input, SimpleChanges } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';

@Component({
    selector: 'jhi-programming-exercise-plans-and-repositories-preview',
    templateUrl: './programming-exercise-plans-and-repositories-preview.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExercisePlansAndRepositoriesPreviewComponent {
    @Input() programmingExercise: ProgrammingExercise | null;
    @Input() isLocal: boolean;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    constructor(private programmingExerciseService: ProgrammingExerciseService) {}

    getCourseShortName(): string | undefined {
        if (!this.programmingExercise) {
            return undefined;
        }
        return getCourseFromExercise(this.programmingExercise)?.shortName;
    }

    solutionCheckoutDirectory: string | undefined;
    exerciseCheckoutDirectory: string | undefined;
    testCheckoutDirectory: string | undefined;

    ngOnInit() {
        this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingExerciseCreationConfig.selectedProgrammingLanguage)
            .subscribe((checkoutDirectories) => {
                this.solutionCheckoutDirectory = checkoutDirectories.solutionCheckoutDirectory;
                this.exerciseCheckoutDirectory = checkoutDirectories.exerciseCheckoutDirectory;
                this.testCheckoutDirectory = checkoutDirectories.testCheckoutDirectory;
            });
    }

    // TODO fix change detection
    ngOnChanges(changes: SimpleChanges) {
        if (
            changes.programmingExerciseCreationConfig &&
            changes.programmingExerciseCreationConfig.currentValue.selectedProgrammingLanguage !==
                changes.programmingExerciseCreationConfig.previousValue.selectedProgrammingLanguage
        ) {
            this.programmingExerciseService
                .getCheckoutDirectoriesForProgrammingLanguage(this.programmingExerciseCreationConfig.selectedProgrammingLanguage)
                .subscribe((checkoutDirectories) => {
                    this.solutionCheckoutDirectory = checkoutDirectories.solutionCheckoutDirectory;
                    this.exerciseCheckoutDirectory = checkoutDirectories.exerciseCheckoutDirectory;
                    this.testCheckoutDirectory = checkoutDirectories.testCheckoutDirectory;
                });
        }
    }
}
