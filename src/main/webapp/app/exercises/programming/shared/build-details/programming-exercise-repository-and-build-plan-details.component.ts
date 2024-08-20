import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { ProgrammingExercise, ProgrammingExerciseBuildConfig, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Subscription } from 'rxjs';
import type { CheckoutDirectoriesDto } from 'app/entities/checkout-directories-dto';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/entities/build-plan-checkout-directories-dto';

@Component({
    selector: 'jhi-programming-exercise-repository-and-build-plan-details',
    templateUrl: './programming-exercise-repository-and-build-plan-details.component.html',
    styleUrls: ['../../manage/programming-exercise-form.scss'],
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule, ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent],
})
export class ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent implements OnInit, OnChanges, OnDestroy {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingLanguage?: ProgrammingLanguage;
    @Input() isLocal: boolean;
    @Input() checkoutSolutionRepository?: boolean = true;
    @Output() submissionBuildPlanEvent = new EventEmitter<BuildPlanCheckoutDirectoriesDTO>();

    constructor(private programmingExerciseService: ProgrammingExerciseService) {}

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
        if (this.isLocal && this.programmingExercise.buildConfig) {
            this.checkoutDirectories = {
                solutionBuildPlanCheckoutDirectories: {
                    solutionCheckoutDirectory:
                        this.programmingExercise.buildConfig?.assignmentCheckoutPath || this.checkoutDirectories?.solutionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory,
                    testCheckoutDirectory:
                        this.programmingExercise.buildConfig?.testCheckoutPath || this.checkoutDirectories?.solutionBuildPlanCheckoutDirectories?.testCheckoutDirectory || '/',
                },
                submissionBuildPlanCheckoutDirectories: {
                    exerciseCheckoutDirectory:
                        this.programmingExercise.buildConfig?.assignmentCheckoutPath || this.checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.exerciseCheckoutDirectory,
                    solutionCheckoutDirectory:
                        this.programmingExercise.buildConfig?.solutionCheckoutPath || this.checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory,
                    testCheckoutDirectory:
                        this.programmingExercise.buildConfig?.testCheckoutPath || this.checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.testCheckoutDirectory || '/',
                },
            };
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
                this.submissionBuildPlanEvent.emit(checkoutDirectories.submissionBuildPlanCheckoutDirectories!);
            });
    }

    private updateCourseShortName() {
        if (!this.programmingExercise) {
            return;
        }
        this.courseShortName = getCourseFromExercise(this.programmingExercise)?.shortName;
    }

    private isBuildConfigCheckoutPathsUpdated(newValue?: ProgrammingExerciseBuildConfig, oldValue?: ProgrammingExerciseBuildConfig): boolean {
        return (
            newValue?.assignmentCheckoutPath !== oldValue?.assignmentCheckoutPath ||
            newValue?.testCheckoutPath !== oldValue?.testCheckoutPath ||
            newValue?.solutionCheckoutPath !== oldValue?.solutionCheckoutPath
        );
    }
}
