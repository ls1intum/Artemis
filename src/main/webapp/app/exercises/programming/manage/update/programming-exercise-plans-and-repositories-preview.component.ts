import { Component, Input, SimpleChanges } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-plans-and-repositories-preview',
    templateUrl: './programming-exercise-plans-and-repositories-preview.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExercisePlansAndRepositoriesPreviewComponent {
    @Input() programmingExercise: ProgrammingExercise | null;
    @Input() isLocal: boolean;

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
        if (!this.programmingExercise?.programmingLanguage) {
            return;
        }

        this.programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage(this.programmingExercise?.programmingLanguage).subscribe((checkoutDirectories) => {
            if (this.programmingExercise) {
                this.solutionCheckoutDirectory = checkoutDirectories.solutionCheckoutDirectory;
                this.exerciseCheckoutDirectory = checkoutDirectories.exerciseCheckoutDirectory;
                this.testCheckoutDirectory = checkoutDirectories.testCheckoutDirectory;
            }
        });
    }

    // TODO fix change detection
    ngOnChanges(changes: SimpleChanges) {
        if (
            changes.programmingExercise &&
            changes.programmingExercise.currentValue.programmingLanguage !== changes.programmingExercise.previousValue.programmingLanguage &&
            this.programmingExercise?.programmingLanguage
        ) {
            this.programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage(this.programmingExercise?.programmingLanguage).subscribe((checkoutDirectories) => {
                if (this.programmingExercise) {
                    this.solutionCheckoutDirectory = checkoutDirectories.solutionCheckoutDirectory;
                    this.exerciseCheckoutDirectory = checkoutDirectories.exerciseCheckoutDirectory;
                    this.testCheckoutDirectory = checkoutDirectories.testCheckoutDirectory;
                }
            });
        }
    }
}
