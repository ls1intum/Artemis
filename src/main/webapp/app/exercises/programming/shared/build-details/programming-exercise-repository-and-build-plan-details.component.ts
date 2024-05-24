import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import type { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Subscription } from 'rxjs';
import type { CheckoutDirectoriesDto } from 'app/entities/checkout-directories-dto';

@Component({
    selector: 'jhi-programming-exercise-repository-and-build-plan-details',
    templateUrl: './programming-exercise-repository-and-build-plan-details.component.html',
    styleUrls: ['../../manage/programming-exercise-form.scss'],
})
export class ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent implements OnInit, OnChanges, OnDestroy {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingLanguage?: ProgrammingLanguage;
    @Input() isLocal: boolean;

    constructor(private programmingExerciseService: ProgrammingExerciseService) {}

    checkoutDirectorySubscription?: Subscription;

    courseShortName?: string;
    checkoutDirectories?: CheckoutDirectoriesDto;
    auxiliaryRepositoryCheckoutDirectories: string[] = [];

    private updateCheckoutDirectories() {
        if (!this.programmingLanguage) {
            return;
        }

        if (this.checkoutDirectorySubscription) {
            this.checkoutDirectorySubscription.unsubscribe();
        }

        this.checkoutDirectorySubscription = this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingLanguage)
            .subscribe((checkoutDirectories) => {
                this.checkoutDirectories = checkoutDirectories;
            });
    }

    private updateCourseShortName() {
        if (!this.programmingExercise) {
            return;
        }
        this.courseShortName = getCourseFromExercise(this.programmingExercise)?.shortName;
    }

    ngOnInit() {
        this.updateCourseShortName();

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
        this.checkoutDirectorySubscription?.unsubscribe();
    }
}
