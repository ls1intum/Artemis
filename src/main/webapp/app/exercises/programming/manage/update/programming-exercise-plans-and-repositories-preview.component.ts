import { Component, Input, SimpleChanges } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { Subscription } from 'rxjs';

const ROOT_DIRECTORY: string = '/';

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
    auxiliaryRepositoryCheckoutDirectories: string[] = [];

    programmingExerciseServiceSubscription: Subscription;

    private addLeadingSlashIfNotPresent(directory: string | undefined): string {
        if (!directory) {
            return ROOT_DIRECTORY;
        }

        return directory.startsWith(ROOT_DIRECTORY) ? directory : ROOT_DIRECTORY + directory;
    }

    private updateCheckoutDirectories() {
        this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingExerciseCreationConfig.selectedProgrammingLanguage)
            .subscribe((checkoutDirectories) => {
                this.solutionCheckoutDirectory = this.addLeadingSlashIfNotPresent(checkoutDirectories.solutionCheckoutDirectory);
                this.exerciseCheckoutDirectory = this.addLeadingSlashIfNotPresent(checkoutDirectories.exerciseCheckoutDirectory);
                this.testCheckoutDirectory = this.addLeadingSlashIfNotPresent(checkoutDirectories.testCheckoutDirectory);
            });
    }

    private updateAuxiliaryRepositoryCheckoutDirectories() {
        this.auxiliaryRepositoryCheckoutDirectories =
            this.programmingExercise?.auxiliaryRepositories?.map((auxiliaryRepository) => this.addLeadingSlashIfNotPresent(auxiliaryRepository.checkoutDirectory)) ?? [];
    }

    ngOnInit() {
        if (this.isLocal) {
            this.updateCheckoutDirectories();
            this.updateAuxiliaryRepositoryCheckoutDirectories();
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (
            this.isLocal &&
            this.programmingExerciseCreationConfig &&
            this.programmingExerciseCreationConfig.selectedProgrammingLanguage &&
            changes.programmingExerciseCreationConfig &&
            changes.programmingExerciseCreationConfig.currentValue &&
            changes.programmingExerciseCreationConfig.currentValue.selectedProgrammingLanguage !==
                changes.programmingExerciseCreationConfig.previousValue?.selectedProgrammingLanguage
        ) {
            this.updateCheckoutDirectories();
        }

        if (this.isLocal && this.programmingExercise?.auxiliaryRepositories) {
            this.updateAuxiliaryRepositoryCheckoutDirectories();
        }
    }

    ngOnDestroy() {
        if (this.programmingExerciseServiceSubscription) {
            this.programmingExerciseServiceSubscription.unsubscribe();
        }
    }
}
