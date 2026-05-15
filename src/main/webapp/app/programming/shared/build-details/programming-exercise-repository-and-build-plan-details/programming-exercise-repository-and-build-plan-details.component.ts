import { Component, OnDestroy, OnInit, effect, inject, input, output, signal, untracked } from '@angular/core';
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
export class ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent implements OnInit, OnDestroy {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private profileService = inject(ProfileService);

    readonly programmingExercise = input.required<ProgrammingExercise>();
    readonly programmingExerciseBuildConfig = input<ProgrammingExerciseBuildConfig>();
    readonly programmingLanguage = input<ProgrammingLanguage>();
    readonly checkoutSolutionRepository = input(true);
    readonly isCreateOrEdit = input(false);
    readonly isEditMode = input(false);
    readonly submissionBuildPlanEvent = output<BuildPlanCheckoutDirectoriesDTO>();

    checkoutDirectorySubscription?: Subscription;
    readonly courseShortName = signal<string | undefined>(undefined);
    readonly checkoutDirectories = signal<CheckoutDirectoriesDto | undefined>(undefined);

    readonly isLocalCIEnabled = signal<boolean>(true);

    // Single snapshot of the previous tracked-input values; consolidates the previousX fields.
    private previousInputs: { programmingLanguage?: ProgrammingLanguage; checkoutSolutionRepository?: boolean } = {};

    constructor() {
        effect(() => {
            const currentProgrammingLanguage = this.programmingLanguage();
            const currentCheckoutSolutionRepository = this.checkoutSolutionRepository();
            // Parent pushes buildConfig-only updates through this input while keeping the exercise
            // reference stable; read it so the effect tracks that channel too.
            this.programmingExerciseBuildConfig();
            const currentProgrammingExercise = this.programmingExercise();

            const programmingLanguageChanged = currentProgrammingLanguage !== this.previousInputs.programmingLanguage;
            const checkoutSolutionRepositoryChanged = currentCheckoutSolutionRepository !== this.previousInputs.checkoutSolutionRepository;
            this.previousInputs = { programmingLanguage: currentProgrammingLanguage, checkoutSolutionRepository: currentCheckoutSolutionRepository };

            untracked(() => {
                if (this.isLocalCIEnabled() && (programmingLanguageChanged || checkoutSolutionRepositoryChanged)) {
                    if (this.isCreateOrEdit() && !this.isEditMode()) {
                        this.resetProgrammingExerciseBuildCheckoutPaths();
                    }
                    this.updateCheckoutDirectories();
                }

                if (this.isCreateOrEdit() && this.isBuildConfigAvailable(currentProgrammingExercise.buildConfig)) {
                    this.checkoutDirectories.set(this.setCheckoutDirectoriesFromBuildConfig(this.checkoutDirectories()));
                }
            });
        });
    }

    ngOnInit() {
        this.isLocalCIEnabled.set(this.profileService.isProfileActive(PROFILE_LOCALCI));
        this.updateCourseShortName();
        if (this.isLocalCIEnabled()) {
            this.updateCheckoutDirectories();
        }
        if (this.isCreateOrEdit() && this.isBuildConfigAvailable(this.programmingExercise().buildConfig)) {
            this.checkoutDirectories.set(this.setCheckoutDirectoriesFromBuildConfig(this.checkoutDirectories()));
        }
        // Prime previous-input snapshot before the effect's first tick so it sees no spurious change.
        this.previousInputs = {
            programmingLanguage: this.programmingLanguage(),
            checkoutSolutionRepository: this.checkoutSolutionRepository(),
        };
    }

    ngOnDestroy() {
        this.checkoutDirectorySubscription?.unsubscribe();
    }

    private updateCheckoutDirectories() {
        const programmingLanguage = this.programmingLanguage();
        if (!programmingLanguage) {
            return;
        }

        this.checkoutDirectorySubscription?.unsubscribe(); // might be defined from previous method execution

        const checkoutSolutionRepositoryDefault = true;
        this.checkoutDirectorySubscription = this.programmingExerciseService
            .getCheckoutDirectoriesForProgrammingLanguage(programmingLanguage, this.checkoutSolutionRepository() ?? checkoutSolutionRepositoryDefault)
            .subscribe((checkoutDirectories) => {
                if ((this.isCreateOrEdit() && !this.isEditMode()) || !this.isBuildConfigAvailable(this.programmingExercise().buildConfig)) {
                    this.checkoutDirectories.set(checkoutDirectories);
                    this.submissionBuildPlanEvent.emit(checkoutDirectories.submissionBuildPlanCheckoutDirectories!);
                } else {
                    this.checkoutDirectories.set(this.setCheckoutDirectoriesFromBuildConfig(checkoutDirectories));
                }
            });
    }

    private setCheckoutDirectoriesFromBuildConfig(checkoutDirectories?: CheckoutDirectoriesDto): CheckoutDirectoriesDto | undefined {
        const programmingExercise = this.programmingExercise();
        if (programmingExercise.buildConfig || checkoutDirectories) {
            checkoutDirectories = {
                solutionBuildPlanCheckoutDirectories: {
                    solutionCheckoutDirectory:
                        this.addLeadingSlash(programmingExercise.buildConfig?.assignmentCheckoutPath) ||
                        checkoutDirectories?.solutionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory,
                    testCheckoutDirectory:
                        this.addLeadingSlash(programmingExercise.buildConfig?.testCheckoutPath) ||
                        checkoutDirectories?.solutionBuildPlanCheckoutDirectories?.testCheckoutDirectory ||
                        '/',
                },
                submissionBuildPlanCheckoutDirectories: {
                    exerciseCheckoutDirectory:
                        this.addLeadingSlash(programmingExercise.buildConfig?.assignmentCheckoutPath) ||
                        checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.exerciseCheckoutDirectory,
                    solutionCheckoutDirectory:
                        this.addLeadingSlash(programmingExercise.buildConfig?.solutionCheckoutPath) ||
                        checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory,
                    testCheckoutDirectory:
                        this.addLeadingSlash(programmingExercise.buildConfig?.testCheckoutPath) ||
                        checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.testCheckoutDirectory ||
                        '/',
                },
            };
        }
        return checkoutDirectories;
    }

    private updateCourseShortName() {
        this.courseShortName.set(getCourseFromExercise(this.programmingExercise())?.shortName);
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
        this.programmingExercise().buildConfig!.assignmentCheckoutPath = undefined;
        this.programmingExercise().buildConfig!.testCheckoutPath = undefined;
        this.programmingExercise().buildConfig!.solutionCheckoutPath = undefined;
    }
}
