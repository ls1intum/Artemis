import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import type { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Subscription } from 'rxjs';
import type { CheckoutDirectoriesDto } from 'app/entities/programming/checkout-directories-dto';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';

@Component({
    selector: 'jhi-programming-exercise-repository-and-build-plan-details',
    templateUrl: './programming-exercise-repository-and-build-plan-details.component.html',
    styleUrls: ['../../manage/programming-exercise-form.scss'],
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule, ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent],
})
export class ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent implements OnInit, OnChanges, OnDestroy {
    private programmingExerciseService = inject(ProgrammingExerciseService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingLanguage?: ProgrammingLanguage;
    @Input() isLocal: boolean;
    @Input() checkoutSolutionRepository?: boolean = true;

    checkoutDirectorySubscription?: Subscription;

    courseShortName?: string;
    checkoutDirectories?: CheckoutDirectoriesDto;

    ngOnInit() {
        this.updateCourseShortName();

        if (this.isLocal) {
            this.updateCheckoutDirectories();
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        const isProgrammingLanguageUpdated = changes.programmingLanguage?.currentValue !== changes.programmingLanguage?.previousValue;
        const isCheckoutSolutionRepositoryUpdated = changes.checkoutSolutionRepository?.currentValue !== changes.checkoutSolutionRepository?.previousValue;
        if (this.isLocal && (isProgrammingLanguageUpdated || isCheckoutSolutionRepositoryUpdated)) {
            this.updateCheckoutDirectories();
        }
    }

    ngOnDestroy() {
        this.checkoutDirectorySubscription?.unsubscribe();
    }

    private updateCheckoutDirectories() {
        if (!this.programmingLanguage) {
            return;
        }

        this.checkoutDirectorySubscription?.unsubscribe(); // might be defined from previous method execution

        const CHECKOUT_SOLUTION_REPOSITORY_DEFAULT = true;
        this.checkoutDirectorySubscription = this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingLanguage, this.checkoutSolutionRepository ?? CHECKOUT_SOLUTION_REPOSITORY_DEFAULT)
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
}
