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
    courseShortName?: string;
    readonly checkoutDirectories = signal<CheckoutDirectoriesDto | undefined>(undefined);

    isLocalCIEnabled = true;

    constructor() {
        // Mirrors the legacy ngOnChanges: react to programmingLanguage / checkoutSolutionRepository /
        // programmingExercise / programmingExerciseBuildConfig changes. The parent updates checkout paths
        // by replacing only `programmingExercise().buildConfig` and pushing it through the separate
        // `programmingExerciseBuildConfig` input while keeping the exercise object stable — so the effect
        // must track that input too, or buildConfig-only edits would not refresh the preview.
        let initialized = false;
        let lastProgrammingLanguage: ProgrammingLanguage | undefined;
        let lastCheckoutSolutionRepository: boolean | undefined;
        effect(() => {
            const currentProgrammingLanguage = this.programmingLanguage();
            const currentCheckoutSolutionRepository = this.checkoutSolutionRepository();
            const currentProgrammingExercise = this.programmingExercise();
            // Track the buildConfig signal too — buildConfig-only updates must trigger this effect.
            this.programmingExerciseBuildConfig();

            const isProgrammingLanguageUpdated = initialized && currentProgrammingLanguage !== lastProgrammingLanguage;
            const isCheckoutSolutionRepositoryUpdated = initialized && currentCheckoutSolutionRepository !== lastCheckoutSolutionRepository;
            lastProgrammingLanguage = currentProgrammingLanguage;
            lastCheckoutSolutionRepository = currentCheckoutSolutionRepository;

            if (!initialized) {
                initialized = true;
                return;
            }

            untracked(() => {
                if (this.isLocalCIEnabled && (isProgrammingLanguageUpdated || isCheckoutSolutionRepositoryUpdated)) {
                    if (this.isCreateOrEdit() && !this.isEditMode()) {
                        this.resetProgrammingExerciseBuildCheckoutPaths();
                    }
                    this.updateCheckoutDirectories();
                }

                const isBuildConfigChanged = this.isBuildConfigAvailable(currentProgrammingExercise.buildConfig);
                if (this.isCreateOrEdit() && isBuildConfigChanged) {
                    this.checkoutDirectories.set(this.setCheckoutDirectoriesFromBuildConfig(this.checkoutDirectories()));
                }
            });
        });
    }

    ngOnInit() {
        this.isLocalCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
        this.updateCourseShortName();
        if (this.isLocalCIEnabled) {
            this.updateCheckoutDirectories();
        }
        // Initial buildConfig-derived population: mirrors the legacy ngOnChanges first-call behavior,
        // which the effect() above intentionally skips. When isCreateOrEdit is true and buildConfig already
        // carries checkout paths at init time, populate checkoutDirectories synchronously so the template
        // does not have to wait for an async service response or a later tracked input change.
        if (this.isCreateOrEdit() && this.isBuildConfigAvailable(this.programmingExercise().buildConfig)) {
            this.checkoutDirectories.set(this.setCheckoutDirectoriesFromBuildConfig(this.checkoutDirectories()));
        }
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
        const programmingExercise = this.programmingExercise();
        if (!programmingExercise) {
            return;
        }
        this.courseShortName = getCourseFromExercise(programmingExercise)?.shortName;
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
