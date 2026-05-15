import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { CheckoutDirectoriesDto } from 'app/programming/shared/entities/checkout-directories-dto';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MockComponent } from 'ng-mocks';
import { Subscription, of } from 'rxjs';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/programming/shared/build-details/programming-exercise-build-plan-checkout-directories/programming-exercise-build-plan-checkout-directories.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/programming/shared/build-details/programming-exercise-repository-and-build-plan-details/programming-exercise-repository-and-build-plan-details.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';

describe('ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent;
    let fixture: ComponentFixture<ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let profileService: ProfileService;

    const CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN = '#checkout-directory-preview-submission-build-plan';
    const CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN = '#checkout-directory-preview-solution-build-plan';

    const JAVA_CHECKOUT_DIRECTORIES: CheckoutDirectoriesDto = {
        submissionBuildPlanCheckoutDirectories: {
            exerciseCheckoutDirectory: '/assignment',
            testCheckoutDirectory: '',
        },
        solutionBuildPlanCheckoutDirectories: {
            solutionCheckoutDirectory: '/assignment',
            testCheckoutDirectory: '',
        },
    };

    const OCAML_CHECKOUT_DIRECTORIES: CheckoutDirectoriesDto = {
        submissionBuildPlanCheckoutDirectories: {
            exerciseCheckoutDirectory: '/assignment',
            solutionCheckoutDirectory: '/solution',
            testCheckoutDirectory: 'tests',
        },
        solutionBuildPlanCheckoutDirectories: {
            solutionCheckoutDirectory: '/assignment',
            testCheckoutDirectory: 'tests',
        },
    };

    const OCAML_CHECKOUT_DIRECTORIES_WITHOUT_SOLUTION_CHECKOUT: CheckoutDirectoriesDto = {
        submissionBuildPlanCheckoutDirectories: {
            exerciseCheckoutDirectory: '/assignment',
            testCheckoutDirectory: 'tests',
        },
        solutionBuildPlanCheckoutDirectories: {
            solutionCheckoutDirectory: '/assignment',
            testCheckoutDirectory: 'tests',
        },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent, MockComponent(HelpIconComponent), ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent],
            providers: [
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent);
        component = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        profileService = TestBed.inject(ProfileService);

        fixture.componentRef.setInput('programmingLanguage', ProgrammingLanguage.C);
        fixture.componentRef.setInput('programmingExercise', { id: 1, shortName: 'shortName', buildConfig: new ProgrammingExerciseBuildConfig() } as ProgrammingExercise);
        component.isLocalCIEnabled.set(true);

        vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage').mockImplementation(
            (programmingLanguage: ProgrammingLanguage, checkoutSolution: boolean) => {
                if (programmingLanguage === ProgrammingLanguage.JAVA) {
                    return of(JAVA_CHECKOUT_DIRECTORIES);
                }

                if (programmingLanguage === ProgrammingLanguage.OCAML && !checkoutSolution) {
                    return of(OCAML_CHECKOUT_DIRECTORIES_WITHOUT_SOLUTION_CHECKOUT);
                }

                return of(OCAML_CHECKOUT_DIRECTORIES);
            },
        );
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should display checkout directories when they exist', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        fixture.detectChanges();

        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);

        expect(submissionPreviewElement).toBeTruthy();
        expect(submissionPreviewElement.textContent).toContain('/assignment');
        expect(submissionPreviewElement.textContent).toContain('/');
        expect(solutionPreviewElement).toBeTruthy();
        expect(solutionPreviewElement.textContent).toContain('/assignment');
        expect(solutionPreviewElement.textContent).toContain('/');
    });

    it('should send request if localCI is used', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        const spy = vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        fixture.detectChanges();

        expect(spy).toHaveBeenCalled();
    });

    it('should NOT send request if localCI is NOT used', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [] } as unknown as ProfileInfo);
        component.isLocalCIEnabled.set(false);
        const spy = vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        fixture.changeDetectorRef.detectChanges();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should display checkoutDirectory preview if localCI is used', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        fixture.detectChanges();
        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeTruthy();
        expect(solutionPreviewElement).toBeTruthy();
    });

    it('should NOT display checkoutDirectory preview if localCI is NOT used', () => {
        component.isLocalCIEnabled.set(false);
        fixture.changeDetectorRef.detectChanges();
        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeFalsy();
        expect(solutionPreviewElement).toBeFalsy();
    });

    it('should update auxiliary checkout repository directories', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        const exercise = component.programmingExercise();
        exercise.auxiliaryRepositories = [{ checkoutDirectory: 'assignment/sut' } as AuxiliaryRepository];

        fixture.changeDetectorRef.detectChanges();

        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeTruthy();
        expect(submissionPreviewElement.textContent).toContain('/assignment/sut'); // the slash for the root directory should have been added
        expect(solutionPreviewElement).toBeTruthy();
        expect(solutionPreviewElement.textContent).toContain('/assignment/sut');
    });

    it('should unsubscribe from programmingExerciseServiceSubscription on destroy', () => {
        const subscription = new Subscription();
        vi.spyOn(subscription, 'unsubscribe');
        component.checkoutDirectorySubscription = subscription;

        component.ngOnDestroy();

        expect(subscription.unsubscribe).toHaveBeenCalled();
    });

    it('should update checkout directories when selectedProgrammingLanguage changes', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        const spy = vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');
        fixture.detectChanges(); // initial run with C

        fixture.componentRef.setInput('programmingLanguage', ProgrammingLanguage.OCAML);
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith(ProgrammingLanguage.OCAML, true);
        expect(component.checkoutDirectories()?.submissionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory).toBe('/solution'); // was null before with JAVA as programming language

        // should also reset build config
        expect(component.programmingExercise().buildConfig?.solutionCheckoutPath).toBeUndefined();
        expect(component.programmingExercise().buildConfig?.testCheckoutPath).toBeUndefined();
        expect(component.programmingExercise().buildConfig?.assignmentCheckoutPath).toBeUndefined();
    });

    it('should update checkout directories when checkoutSolution flag changes', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        const spy = vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');
        fixture.componentRef.setInput('programmingLanguage', ProgrammingLanguage.OCAML);
        fixture.detectChanges();

        fixture.componentRef.setInput('checkoutSolutionRepository', false);
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith(ProgrammingLanguage.OCAML, false);
        // solution checkout directory was /solution before with OCaml as programming language and solution checkout allowed
        expect(component.checkoutDirectories()?.submissionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory).toBeUndefined();

        // should also reset build config
        expect(component.programmingExercise().buildConfig?.solutionCheckoutPath).toBeUndefined();
        expect(component.programmingExercise().buildConfig?.testCheckoutPath).toBeUndefined();
        expect(component.programmingExercise().buildConfig?.assignmentCheckoutPath).toBeUndefined();
    });

    it('should not call service without language', () => {
        const spy = vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');
        fixture.componentRef.setInput('programmingLanguage', undefined);
        fixture.detectChanges();

        fixture.componentRef.setInput('checkoutSolutionRepository', false);
        fixture.detectChanges();

        expect(spy).not.toHaveBeenCalledWith(ProgrammingLanguage.OCAML, false);
    });

    it('should not call service when inEdit and build config is available', () => {
        const spy = vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');
        fixture.componentRef.setInput('isCreateOrEdit', true);
        const exercise = component.programmingExercise();
        exercise.buildConfig = new ProgrammingExerciseBuildConfig();
        exercise.buildConfig.solutionCheckoutPath = 'solution';
        exercise.buildConfig.testCheckoutPath = 'tests';
        exercise.buildConfig.assignmentCheckoutPath = 'assignment';

        fixture.detectChanges(); // first run, initial - no diff yet

        // trigger an effect cycle by changing checkoutSolutionRepository back to true (already true) - we need a real change
        // To simulate the previous ngOnChanges call with same value, we re-set the input to a new value and back.
        fixture.componentRef.setInput('checkoutSolutionRepository', false);
        fixture.detectChanges();
        fixture.componentRef.setInput('checkoutSolutionRepository', true);
        fixture.detectChanges();

        expect(spy).not.toHaveBeenCalledWith(ProgrammingLanguage.OCAML, false);
        expect(component.checkoutDirectories()).toEqual({
            submissionBuildPlanCheckoutDirectories: {
                exerciseCheckoutDirectory: '/assignment',
                testCheckoutDirectory: '/tests',
                solutionCheckoutDirectory: '/solution',
            },
            solutionBuildPlanCheckoutDirectories: {
                solutionCheckoutDirectory: '/assignment',
                testCheckoutDirectory: '/tests',
            },
        });
    });

    it('should update auxiliary repository directories on changes', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        fixture.detectChanges();

        const updatedExercise: ProgrammingExercise = {
            ...component.programmingExercise(),
            auxiliaryRepositories: [{ checkoutDirectory: 'assignment/src' } as AuxiliaryRepository],
        } as ProgrammingExercise;
        fixture.componentRef.setInput('programmingExercise', updatedExercise);
        fixture.changeDetectorRef.detectChanges();

        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeTruthy();
        expect(submissionPreviewElement.textContent).toContain('/assignment/src');
    });

    it('should derive checkoutDirectories from buildConfig on initial detectChanges when isCreateOrEdit is true', () => {
        // localCI disabled so only the synchronous ngOnInit derivation populates the signal here.
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [] } as unknown as ProfileInfo);
        component.isLocalCIEnabled.set(false);

        const buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.assignmentCheckoutPath = 'assignment';
        buildConfig.solutionCheckoutPath = 'solution';
        buildConfig.testCheckoutPath = 'tests';
        fixture.componentRef.setInput('isCreateOrEdit', true);
        fixture.componentRef.setInput('programmingExercise', { id: 1, shortName: 'shortName', buildConfig } as ProgrammingExercise);

        const serviceSpy = vi.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        fixture.detectChanges(); // first detectChanges — no later tracked input change, no async response

        expect(serviceSpy).not.toHaveBeenCalled();
        expect(component.checkoutDirectories()).toEqual({
            solutionBuildPlanCheckoutDirectories: {
                solutionCheckoutDirectory: '/assignment',
                testCheckoutDirectory: '/tests',
            },
            submissionBuildPlanCheckoutDirectories: {
                exerciseCheckoutDirectory: '/assignment',
                solutionCheckoutDirectory: '/solution',
                testCheckoutDirectory: '/tests',
            },
        });
    });

    it('should update component when buildconfig was changed', () => {
        fixture.componentRef.setInput('isCreateOrEdit', true);
        fixture.detectChanges(); // initial pass

        const newBuildConfig = new ProgrammingExerciseBuildConfig();
        newBuildConfig.solutionCheckoutPath = 'solution';
        const updatedExercise: ProgrammingExercise = {
            ...component.programmingExercise(),
            buildConfig: newBuildConfig,
        } as ProgrammingExercise;
        fixture.componentRef.setInput('programmingExercise', updatedExercise);
        fixture.detectChanges();

        expect(component.checkoutDirectories()).toEqual({
            solutionBuildPlanCheckoutDirectories: {
                testCheckoutDirectory: '/',
            },
            submissionBuildPlanCheckoutDirectories: {
                solutionCheckoutDirectory: '/solution',
                testCheckoutDirectory: '/',
            },
        });
    });

    it('should refresh checkout directories when only the programmingExerciseBuildConfig input changes', () => {
        fixture.componentRef.setInput('isCreateOrEdit', true);
        fixture.detectChanges(); // initial pass

        // Mutate buildConfig on the stable exercise reference and push only the buildConfig input.
        const exercise = component.programmingExercise();
        exercise.buildConfig!.assignmentCheckoutPath = 'assignment';
        exercise.buildConfig!.solutionCheckoutPath = 'solution';
        exercise.buildConfig!.testCheckoutPath = 'tests';
        fixture.componentRef.setInput('programmingExerciseBuildConfig', exercise.buildConfig);
        fixture.detectChanges();

        expect(component.checkoutDirectories()).toEqual({
            solutionBuildPlanCheckoutDirectories: {
                solutionCheckoutDirectory: '/assignment',
                testCheckoutDirectory: '/tests',
            },
            submissionBuildPlanCheckoutDirectories: {
                exerciseCheckoutDirectory: '/assignment',
                solutionCheckoutDirectory: '/solution',
                testCheckoutDirectory: '/tests',
            },
        });
    });
});
