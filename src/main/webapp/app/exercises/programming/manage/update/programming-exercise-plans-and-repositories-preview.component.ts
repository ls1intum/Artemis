import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { Subscription } from 'rxjs';
import { addLeadingSlashIfNotPresent } from 'app/shared/util/utils';
import { RepositoriesCheckoutDirectoriesDTO } from 'app/entities/repositories-checkout-directories-dto';

@Component({
    selector: 'jhi-programming-exercise-plans-and-repositories-preview',
    templateUrl: './programming-exercise-plans-and-repositories-preview.component.html',
    styleUrls: ['../programming-exercise-form.scss'],
})
export class ProgrammingExercisePlansAndRepositoriesPreviewComponent implements OnInit, OnChanges, OnDestroy {
    @Input() programmingExercise: ProgrammingExercise | null;
    @Input() isLocal: boolean;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;

    constructor(private programmingExerciseService: ProgrammingExerciseService) {}

    shortName?: string;

    checkoutDirectories?: RepositoriesCheckoutDirectoriesDTO;
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
            this.programmingExercise?.auxiliaryRepositories?.map((auxiliaryRepository) => addLeadingSlashIfNotPresent(auxiliaryRepository.checkoutDirectory)) ?? [];
    }

    private updateShortName() {
        if (!this.programmingExercise) {
            return;
        }
        this.shortName = getCourseFromExercise(this.programmingExercise)?.shortName;
    }

    ngOnInit() {
        this.updateShortName();

        if (this.isLocal) {
            this.updateCheckoutDirectories();
            this.updateAuxiliaryRepositoryCheckoutDirectories();
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.programmingExercise) {
            this.updateShortName();
        }

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
        this.programmingExerciseServiceSubscription?.unsubscribe();
    }
}
