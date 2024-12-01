import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { CheckoutDirectoriesDto } from 'app/entities/programming/checkout-directories-dto';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/entities/programming/programming-exercise-build.config';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent } from 'ng-mocks';
import { Subscription, of } from 'rxjs';
import { AuxiliaryRepository } from 'app/entities/programming/programming-exercise-auxiliary-repository-model';
import { SimpleChanges } from '@angular/core';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';

describe('ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent', () => {
    let component: ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent;
    let fixture: ComponentFixture<ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

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
            declarations: [ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent, MockComponent(HelpIconComponent), ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent],
            imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
            providers: [
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent);
                component = fixture.componentInstance;
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

                component.programmingLanguage = ProgrammingLanguage.C;
                component.programmingExercise = { id: 1, shortName: 'shortName', buildConfig: new ProgrammingExerciseBuildConfig() } as ProgrammingExercise;
                component.isLocal = true;

                jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage').mockImplementation(
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
    });

    it('should display checkout directories when they exist', () => {
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
        jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        fixture.detectChanges();

        expect(programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage).toHaveBeenCalled();
    });

    it('should NOT send request if localCI is NOT used', () => {
        component.isLocal = false;
        const spy = jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        fixture.detectChanges();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should display checkoutDirectory preview if localCI is used', () => {
        fixture.detectChanges();
        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeTruthy();
        expect(solutionPreviewElement).toBeTruthy();
    });

    it('should NOT display checkoutDirectory preview if localCI is NOT used', () => {
        component.isLocal = false;
        fixture.detectChanges();
        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeFalsy();
        expect(solutionPreviewElement).toBeFalsy();
    });

    it('should update auxiliary checkout repository directories', () => {
        component.programmingExercise!.auxiliaryRepositories = [{ checkoutDirectory: 'assignment/sut' } as AuxiliaryRepository];

        fixture.detectChanges();

        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeTruthy();
        expect(submissionPreviewElement.textContent).toContain('/assignment/sut'); // the slash for the root directory should have been added
        expect(solutionPreviewElement).toBeTruthy();
        expect(solutionPreviewElement.textContent).toContain('/assignment/sut');
    });

    it('should unsubscribe from programmingExerciseServiceSubscription on destroy', () => {
        const subscription = new Subscription();
        jest.spyOn(subscription, 'unsubscribe');
        component.checkoutDirectorySubscription = subscription;

        component.ngOnDestroy();

        expect(subscription.unsubscribe).toHaveBeenCalled();
    });

    it('should update checkout directories when selectedProgrammingLanguage changes', () => {
        jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        component.programmingLanguage = ProgrammingLanguage.OCAML;
        component.ngOnChanges({
            programmingLanguage: {
                previousValue: ProgrammingLanguage.JAVA,
                currentValue: ProgrammingLanguage.OCAML,
            },
        } as unknown as SimpleChanges);

        // assertion to check if ngOnChanges was executed properly and updated the checkout directories
        expect(programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage).toHaveBeenCalledWith(ProgrammingLanguage.OCAML, true);
        expect(component.checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory).toBe('/solution'); // was null before with JAVA as programming language

        // should also reset build config
        expect(component.programmingExercise?.buildConfig?.solutionCheckoutPath).toBeUndefined();
        expect(component.programmingExercise?.buildConfig?.testCheckoutPath).toBeUndefined();
        expect(component.programmingExercise?.buildConfig?.assignmentCheckoutPath).toBeUndefined();
    });

    it('should update checkout directories when checkoutSolution flag changes', () => {
        jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        component.programmingLanguage = ProgrammingLanguage.OCAML;
        component.checkoutSolutionRepository = false;
        component.ngOnChanges({
            checkoutSolutionRepository: {
                previousValue: true,
                currentValue: false,
            },
        } as unknown as SimpleChanges);

        // assertion to check if ngOnChanges was executed properly and updated the checkout directories
        expect(programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage).toHaveBeenCalledWith(ProgrammingLanguage.OCAML, false);
        // solution checkout directory was /solution before with OCaml as programming language and solution checkout allowed
        expect(component.checkoutDirectories?.submissionBuildPlanCheckoutDirectories?.solutionCheckoutDirectory).toBeUndefined();

        // should also reset build config
        expect(component.programmingExercise?.buildConfig?.solutionCheckoutPath).toBeUndefined();
        expect(component.programmingExercise?.buildConfig?.testCheckoutPath).toBeUndefined();
        expect(component.programmingExercise?.buildConfig?.assignmentCheckoutPath).toBeUndefined();
    });

    it('should not call service without language', () => {
        jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        component.programmingLanguage = undefined;
        component.checkoutSolutionRepository = false;
        component.ngOnChanges({
            checkoutSolutionRepository: {
                previousValue: true,
                currentValue: false,
            },
        } as unknown as SimpleChanges);

        // assertion to check if ngOnChanges was executed properly and updated the checkout directories
        expect(programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage).not.toHaveBeenCalledWith(ProgrammingLanguage.OCAML, false);
    });

    it('should not call service when inEdit and build config is available', () => {
        jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        component.isCreateOrEdit = true;
        component.programmingExercise!.buildConfig = new ProgrammingExerciseBuildConfig();
        component.programmingExercise!.buildConfig.solutionCheckoutPath = 'solution';
        component.programmingExercise!.buildConfig.testCheckoutPath = 'tests';
        component.programmingExercise!.buildConfig.assignmentCheckoutPath = 'assignment';

        component.ngOnChanges({
            checkoutSolutionRepository: {
                previousValue: true,
                currentValue: true,
            },
        } as unknown as SimpleChanges);

        // assertion to check if ngOnChanges was executed properly and updated the checkout directories
        expect(programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage).not.toHaveBeenCalledWith(ProgrammingLanguage.OCAML, false);
        expect(component.checkoutDirectories).toEqual({
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
        fixture.detectChanges();

        component.programmingExercise!.auxiliaryRepositories = [{ checkoutDirectory: 'assignment/src' } as AuxiliaryRepository];
        component.ngOnChanges({
            programmingExercise: {
                previousValue: { auxiliaryRepositories: [] },
                currentValue: { auxiliaryRepositories: [{ checkoutDirectory: 'assignment/src' } as AuxiliaryRepository] },
            },
        } as unknown as SimpleChanges);

        fixture.detectChanges();

        const submissionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN);
        expect(submissionPreviewElement).toBeTruthy();
        expect(submissionPreviewElement.textContent).toContain('/assignment/src');
    });

    it('should update component when buildconfig was changed', () => {
        component.isCreateOrEdit = true;
        component.programmingExercise!.buildConfig = new ProgrammingExerciseBuildConfig();
        component.programmingExercise!.buildConfig.solutionCheckoutPath = 'solution';

        component.ngOnChanges({
            programmingExercise: {
                previousValue: { buildConfig: new ProgrammingExerciseBuildConfig() },
                currentValue: { buildConfig: component.programmingExercise!.buildConfig },
            },
        } as unknown as SimpleChanges);

        expect(component.checkoutDirectories).toEqual({
            solutionBuildPlanCheckoutDirectories: {
                testCheckoutDirectory: '/',
            },
            submissionBuildPlanCheckoutDirectories: {
                solutionCheckoutDirectory: '/solution',
                testCheckoutDirectory: '/',
            },
        });
    });
});
