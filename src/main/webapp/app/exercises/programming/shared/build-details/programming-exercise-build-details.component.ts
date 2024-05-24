import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
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
export class ProgrammingExerciseBuildDetailsComponent implements OnInit, OnChanges, OnDestroy {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingLanguage: ProgrammingLanguage;
    @Input() isLocal: boolean;

    constructor(private programmingExerciseService: ProgrammingExerciseService) {}

    programmingExerciseServiceSubscription: Subscription;

    courseShortName?: string;
    checkoutDirectories?: CheckoutDirectoriesDto;
    auxiliaryRepositoryCheckoutDirectories: string[] = [];

    private updateCheckoutDirectories() {
        this.programmingExerciseServiceSubscription = this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingLanguage)
            .subscribe((checkoutDirectories) => {
                this.checkoutDirectories = checkoutDirectories;
            });
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
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        if (this.isLocal && changes.programmingLanguage && changes.programmingLanguage.currentValue !== changes.programmingLanguage.previousValue) {
            this.updateCheckoutDirectories();
        }
    }

    ngOnDestroy() {
        this.programmingExerciseServiceSubscription?.unsubscribe();
    }
}
