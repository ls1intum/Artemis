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
            this.resetProgrammingExerciseBuildCheckoutPaths();
            this.updateCheckoutDirectories();
        }

        const isBuildConfigChanged = this.isBuildConfigAvailable(this.programmingExercise.buildConfig);
        if (this.isLocal && isBuildConfigChanged) {
            this.checkoutDirectories = this.setCheckoutDirectoriesFromBuildConfig(this.checkoutDirectories);
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

    private setCheckoutDirectoriesFromBuildConfig(checkoutDirectories?: CheckoutDirectoriesDto): CheckoutDirectoriesDto | undefined {
        if (this.programmingExercise.buildConfig || checkoutDirectories) {
            checkoutDirectories = {
                solutionBuildPlanCheckoutDirectories: {
                    solutionCheckoutDirectory:
                        this.addLeadingSlash(this.programmingExercise.buildConfig?.assignmentCheckoutPath) ||
                        checkoutDirectories?.solutionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory,
                    testCheckoutDirectory:
                        this.addLeadingSlash(this.programmingExercise.buildConfig?.testCheckoutPath) ||
                        checkoutDirectories?.solutionBuildPlanCheckoutDirectories?.testCheckoutDirectory ||
                        '/',
                },
                submissionBuildPlanCheckoutDirectories: {
                    exerciseCheckoutDirectory:
                        this.addLeadingSlash(this.programmingExercise.buildConfig?.assignmentCheckoutPath) ||
                        checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.exerciseCheckoutDirectory,
                    solutionCheckoutDirectory:
                        this.addLeadingSlash(this.programmingExercise.buildConfig?.solutionCheckoutPath) ||
                        checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory,
                    testCheckoutDirectory:
                        this.addLeadingSlash(this.programmingExercise.buildConfig?.testCheckoutPath) ||
                        checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.testCheckoutDirectory ||
                        '/',
                },
            };
        }
        return checkoutDirectories;
    }

    private updateCourseShortName() {
        if (!this.programmingExercise) {
            return;
        }
        this.courseShortName = getCourseFromExercise(this.programmingExercise)?.shortName;
    }

    private addLeadingSlash(path?: string): string | undefined {
        if (!path) {
            return undefined;
        }
        return path.startsWith('/') ? path : `/${path}`;
    }

    private isBuildConfigAvailable(buildConfig?: ProgrammingExerciseBuildConfig): boolean {
        return (
            buildConfig !== undefined &&
            ((buildConfig.assignmentCheckoutPath !== undefined && buildConfig.assignmentCheckoutPath !== '') ||
                (buildConfig.testCheckoutPath !== undefined && buildConfig.testCheckoutPath !== '') ||
                (buildConfig.solutionCheckoutPath !== undefined && buildConfig.solutionCheckoutPath !== ''))
        );
    }

    private resetProgrammingExerciseBuildCheckoutPaths() {
        this.programmingExercise.buildConfig!.assignmentCheckoutPath = undefined;
        this.programmingExercise.buildConfig!.testCheckoutPath = undefined;
        this.programmingExercise.buildConfig!.solutionCheckoutPath = undefined;
    }
}
