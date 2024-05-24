import { Component, Input, SimpleChanges } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Subscription } from 'rxjs';
import { CheckoutDirectoriesDto } from 'app/entities/checkout-directories-dto';

@Component({
    selector: 'jhi-programming-exercise-build-details',
    templateUrl: './programming-exercise-build-details.component.html',
    styleUrls: ['../../manage/programming-exercise-form.scss'],
})
export class ProgrammingExerciseBuildDetailsComponent {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() checkoutDirectories?: CheckoutDirectoriesDto;
    @Input() auxiliaryRepositoryCheckoutDirectories: string[];
    @Input() programmingLanguage: ProgrammingLanguage;
    @Input() isLocal: boolean;

    constructor(private programmingExerciseService: ProgrammingExerciseService) {}

    courseShortName?: string;

    programmingExerciseServiceSubscription: Subscription;

    private updateCheckoutDirectories() {
        this.programmingExerciseServiceSubscription = this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingLanguage)
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

    private updateShortName() {
        if (!this.programmingExercise) {
            return;
        }
        this.courseShortName = getCourseFromExercise(this.programmingExercise)?.shortName;
    }

    ngOnInit() {
        this.updateShortName();

        if (this.isLocal) {
            this.updateCheckoutDirectories();
            this.updateAuxiliaryRepositoryCheckoutDirectories();
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (this.isLocal && changes.programmingLanguage && changes.programmingLanguage.currentValue !== changes.programmingLanguage.previousValue) {
            this.updateCheckoutDirectories();
        }

        if (changes.auxiliaryRepositoryCheckoutDirectories) {
            this.updateAuxiliaryRepositoryCheckoutDirectories();
        }
    }

    ngOnDestroy() {
        this.programmingExerciseServiceSubscription?.unsubscribe();
    }
}
