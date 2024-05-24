import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { Subscription } from 'rxjs';
import { CheckoutDirectoriesDto } from 'app/entities/checkout-directories-dto';

@Component({
    selector: 'jhi-programming-exercise-plans-and-repositories-preview',
    templateUrl: './programming-exercise-plans-and-repositories-preview.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExercisePlansAndRepositoriesPreviewComponent implements OnChanges, OnDestroy {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() isLocal: boolean;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    constructor(private programmingExerciseService: ProgrammingExerciseService) {}

    checkoutDirectories?: CheckoutDirectoriesDto;
    auxiliaryRepositoryCheckoutDirectories: string[] = [];

    programmingExerciseServiceSubscription: Subscription;

    private updateCheckoutDirectories() {
        this.programmingExerciseServiceSubscription = this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingExerciseCreationConfig.selectedProgrammingLanguage)
            .subscribe((checkoutDirectories) => {
                this.checkoutDirectories = checkoutDirectories;
            });
    }

    private updateAuxiliaryRepositoryCheckoutDirectories() {
        this.auxiliaryRepositoryCheckoutDirectories =
            this.programmingExercise?.auxiliaryRepositories?.map((auxiliaryRepository) => this.addLeadingSlashIfNotPresent(auxiliaryRepository.checkoutDirectory)) ?? [];
    }

    private addLeadingSlashIfNotPresent(directory: string | undefined): string {
        const ROOT_DIRECTORY_PATH: string = '/';

        if (!directory) {
            return ROOT_DIRECTORY_PATH;
        }

        return directory.startsWith(ROOT_DIRECTORY_PATH) ? directory : ROOT_DIRECTORY_PATH + directory;
    }

    ngOnChanges(changes: SimpleChanges) {
        if (
            this.isLocal &&
            this.programmingExerciseCreationConfig?.selectedProgrammingLanguage &&
            changes.programmingExerciseCreationConfig?.currentValue?.selectedProgrammingLanguage !==
                changes.programmingExerciseCreationConfig?.previousValue?.selectedProgrammingLanguage
        ) {
            this.updateCheckoutDirectories();
        }

        if (this.isLocal && this.programmingExercise?.auxiliaryRepositories) {
            this.updateAuxiliaryRepositoryCheckoutDirectories();
        }
    }

    ngOnDestroy() {
        this.programmingExerciseServiceSubscription?.unsubscribe();
    }
}
