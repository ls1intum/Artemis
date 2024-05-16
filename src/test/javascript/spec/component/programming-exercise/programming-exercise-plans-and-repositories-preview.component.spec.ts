import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { RepositoriesCheckoutDirectoriesDTO } from 'app/exercises/programming/manage/repositories-checkout-directories-dto';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent } from 'ng-mocks';
import { Subscription, of } from 'rxjs';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { SimpleChanges } from '@angular/core';

describe('ProgrammingExercisePlansAndRepositoriesPreviewComponent', () => {
    let component: ProgrammingExercisePlansAndRepositoriesPreviewComponent;
    let fixture: ComponentFixture<ProgrammingExercisePlansAndRepositoriesPreviewComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    const CHECKOUT_DIRECTORY_PREVIEW = '#checkout-directory-preview';

    const JAVA_CHECKOUT_DIRECTORIES: RepositoriesCheckoutDirectoriesDTO = {
        exerciseCheckoutDirectory: '/assignment',
        solutionCheckoutDirectory: '/assignment',
        testCheckoutDirectory: '', // Empty string should be converted to '/'
    };

    const OCAML_CHECKOUT_DIRECTORIES: RepositoriesCheckoutDirectoriesDTO = {
        exerciseCheckoutDirectory: '/assignment',
        solutionCheckoutDirectory: '/solution',
        testCheckoutDirectory: '/tests',
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ProgrammingExercisePlansAndRepositoriesPreviewComponent, MockComponent(HelpIconComponent)],
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

        const previewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW);
        expect(previewElement).toBeTruthy();
        expect(previewElement.textContent).toContain('/assignment');
        expect(previewElement.textContent).toContain('/assignment');
        expect(previewElement.textContent).toContain('/');
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
        const previewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW);
        expect(previewElement).toBeTruthy();
    });

    it('should NOT display checkoutDirectory preview if localCI is NOT used', () => {
        component.isLocal = false;
        fixture.detectChanges();
        const previewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW);
        expect(previewElement).toBeFalsy();
    });

    it('should update auxiliary checkout repository directories', () => {
        component.programmingExercise!.auxiliaryRepositories = [{ checkoutDirectory: 'assignment/sut' } as AuxiliaryRepository];

        fixture.detectChanges();

        const previewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW);
        expect(previewElement).toBeTruthy();
        expect(previewElement.textContent).toContain('/assignment/sut'); // the slash for the root directory should have been added
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
        expect(component.checkoutDirectories?.solutionCheckoutDirectory).toBe('/solution'); // was '/assignment' before with JAVA as programming language
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

        const previewElement = fixture.debugElement.nativeElement.querySelector(CHECKOUT_DIRECTORY_PREVIEW);
        expect(previewElement).toBeTruthy();
        expect(previewElement.textContent).toContain('/assignment/src');
    });
});
