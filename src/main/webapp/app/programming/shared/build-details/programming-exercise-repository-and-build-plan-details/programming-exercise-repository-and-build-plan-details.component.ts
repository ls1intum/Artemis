import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import type { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { Subscription } from 'rxjs';
import type { CheckoutDirectoriesDto } from 'app/programming/shared/entities/checkout-directories-dto';

import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/programming/shared/build-details/programming-exercise-build-plan-checkout-directories/programming-exercise-build-plan-checkout-directories.component';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CommonModule } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-exercise-repository-and-build-plan-details',
    templateUrl: './programming-exercise-repository-and-build-plan-details.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
    imports: [ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent, HelpIconComponent, CommonModule, TranslateDirective],
})
export class ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent implements OnInit, OnChanges, OnDestroy {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private profileService = inject(ProfileService);

    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseBuildConfig?: ProgrammingExerciseBuildConfig;
    @Input() programmingLanguage?: ProgrammingLanguage;
    @Input() checkoutSolutionRepository = true;
    @Input() isCreateOrEdit = false;
    @Input() isEditMode = false;
    @Output() submissionBuildPlanEvent = new EventEmitter<BuildPlanCheckoutDirectoriesDTO>();

    checkoutDirectorySubscription?: Subscription;
    courseShortName?: string;
    checkoutDirectories?: CheckoutDirectoriesDto;

    isLocalCIEnabled = true;

    ngOnInit() {
        this.isLocalCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
        this.updateCourseShortName();
        if (this.isLocalCIEnabled) {
            this.updateCheckoutDirectories();
        }
    }

    ngOnChanges(changes: SimpleChanges) {
        const isProgrammingLanguageUpdated = changes.programmingLanguage?.currentValue !== changes.programmingLanguage?.previousValue;
        const isCheckoutSolutionRepositoryUpdated = changes.checkoutSolutionRepository?.currentValue !== changes.checkoutSolutionRepository?.previousValue;
        if (this.isLocalCIEnabled && (isProgrammingLanguageUpdated || isCheckoutSolutionRepositoryUpdated)) {
            if (this.isCreateOrEdit && !this.isEditMode) {
                this.resetProgrammingExerciseBuildCheckoutPaths();
            }
            this.updateCheckoutDirectories();
        }

        const isBuildConfigChanged = this.isBuildConfigAvailable(this.programmingExercise.buildConfig);
        if (this.isCreateOrEdit && isBuildConfigChanged) {
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

        const checkoutSolutionRepositoryDefault = true;
        this.checkoutDirectorySubscription = this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(this.programmingLanguage, this.checkoutSolutionRepository ?? checkoutSolutionRepositoryDefault)
            .subscribe((checkoutDirectories) => {
                if ((this.isCreateOrEdit && !this.isEditMode) || !this.isBuildConfigAvailable(this.programmingExercise.buildConfig)) {
                    this.checkoutDirectories = checkoutDirectories;
                    this.submissionBuildPlanEvent.emit(checkoutDirectories.submissionBuildPlanCheckoutDirectories!);
                } else {
                    this.checkoutDirectories = this.setCheckoutDirectoriesFromBuildConfig(checkoutDirectories);
                }
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
            ((buildConfig.assignmentCheckoutPath !== undefined && buildConfig.assignmentCheckoutPath.trim() !== '') ||
                (buildConfig.testCheckoutPath !== undefined && buildConfig.testCheckoutPath.trim() !== '') ||
                (buildConfig.solutionCheckoutPath !== undefined && buildConfig.solutionCheckoutPath.trim() !== ''))
        );
    }

    private resetProgrammingExerciseBuildCheckoutPaths() {
        this.programmingExercise.buildConfig!.assignmentCheckoutPath = undefined;
        this.programmingExercise.buildConfig!.testCheckoutPath = undefined;
        this.programmingExercise.buildConfig!.solutionCheckoutPath = undefined;
    }
}
