import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { CheckoutDirectoriesDto } from 'app/entities/checkout-directories-dto';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent } from 'ng-mocks';
import { Subscription, of } from 'rxjs';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { SimpleChanges } from '@angular/core';
import { ProgrammingExerciseBuildPlanDetailsComponent } from 'app/exercises/programming/manage/update/programming-exercise-build-plan-details.component';

describe('ProgrammingExercisePlansAndRepositoriesPreviewComponent', () => {
    let component: ProgrammingExercisePlansAndRepositoriesPreviewComponent;
    let fixture: ComponentFixture<ProgrammingExercisePlansAndRepositoriesPreviewComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    const CHECKOUT_DIRECTORY_PREVIEW_SUBMISSION_BUILD_PLAN = '#checkout-directory-preview-submission-build-plan';
    const CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN = '#checkout-directory-preview-solution-build-plan';

    const JAVA_CHECKOUT_DIRECTORIES: CheckoutDirectoriesDto = {
        submissionBuildPlanCheckoutDirectories: {
            exerciseCheckoutDirectory: '/assignment',
            testCheckoutDirectory: '',
        },
        solutionBuildPlanCheckoutDirectories: {
            solutionCheckoutDirectories: ['/assignment'],
            testCheckoutDirectory: '',
        },
    };

    const OCAML_CHECKOUT_DIRECTORIES: CheckoutDirectoriesDto = {
        submissionBuildPlanCheckoutDirectories: {
            exerciseCheckoutDirectory: '/assignment',
            solutionCheckoutDirectories: ['/solution'],
            testCheckoutDirectory: 'tests',
        },
        solutionBuildPlanCheckoutDirectories: {
            solutionCheckoutDirectories: ['/assignment', '/solution'],
            testCheckoutDirectory: 'tests',
        },
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ProgrammingExercisePlansAndRepositoriesPreviewComponent, MockComponent(HelpIconComponent), ProgrammingExerciseBuildPlanDetailsComponent],
            providers: [{ provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExercisePlansAndRepositoriesPreviewComponent);
                component = fixture.componentInstance;
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);

                component.programmingExerciseCreationConfig = { selectedProgrammingLanguage: ProgrammingLanguage.C } as ProgrammingExerciseCreationConfig;
                component.programmingExercise = { id: 1, shortName: 'shortName' } as ProgrammingExercise;
                component.isLocal = true;

                jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage').mockImplementation((programmingLanguage: ProgrammingLanguage) => {
                    if (programmingLanguage === ProgrammingLanguage.JAVA) {
                        return of(JAVA_CHECKOUT_DIRECTORIES);
                    }

                    return of(OCAML_CHECKOUT_DIRECTORIES);
                });
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
        component.programmingExerciseServiceSubscription = subscription;

        component.ngOnDestroy();

        expect(subscription.unsubscribe).toHaveBeenCalled();
    });

    it('should update checkout directories when selectedProgrammingLanguage changes', () => {
        jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage');

        component.ngOnChanges({
            programmingExerciseCreationConfig: {
                previousValue: { selectedProgrammingLanguage: ProgrammingLanguage.JAVA },
                currentValue: { selectedProgrammingLanguage: ProgrammingLanguage.OCAML },
            },
        } as unknown as SimpleChanges);

        // assertion to check if ngOnChanges was executed properly and updated the checkout directories
        expect(programmingExerciseService.getCheckoutDirectoriesForProgrammingLanguage).toHaveBeenCalled();
        expect(component.checkoutDirectories?.solutionBuildPlanCheckoutDirectories?.solutionCheckoutDirectories).toEqual(['/assignment', '/solution']); // was ['/assignment'] before with JAVA as programming language
    });

    it('should comma separate the solution directories', () => {
        component.ngOnChanges({
            programmingExerciseCreationConfig: {
                previousValue: { selectedProgrammingLanguage: ProgrammingLanguage.JAVA },
                currentValue: { selectedProgrammingLanguage: ProgrammingLanguage.OCAML },
            },
        } as unknown as SimpleChanges);

        fixture.detectChanges();

        const OCAML_SOLUTION_CHECKOUT_DIRECTORY_REGEX = RegExp('.*\\/assignment, *\\/solution.*');
        const solutionPreviewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW_SOLUTION_BUILD_PLAN);
        expect(solutionPreviewElement).toBeTruthy();
        expect(OCAML_SOLUTION_CHECKOUT_DIRECTORY_REGEX.test(solutionPreviewElement.textContent)).toBeTrue();
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
});
