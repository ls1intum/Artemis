import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { RepositoriesCheckoutDirectoriesDTO } from 'app/exercises/programming/manage/repositories-checkout-directories-dto';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/programming-exercise-creation-config';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent } from 'ng-mocks';
import { of } from 'rxjs';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';

describe('ProgrammingExercisePlansAndRepositoriesPreviewComponent', () => {
    let component: ProgrammingExercisePlansAndRepositoriesPreviewComponent;
    let fixture: ComponentFixture<ProgrammingExercisePlansAndRepositoriesPreviewComponent>;
    let programmingExerciseService: ProgrammingExerciseService;

    const CHECKOUT_DIRECTORY_PREVIEW = '#checkout-directory-preview';

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

                const checkoutDirectories: RepositoriesCheckoutDirectoriesDTO = {
                    solutionCheckoutDirectory: '/assignment',
                    exerciseCheckoutDirectory: '/assignment',
                    testCheckoutDirectory: '', // Empty string should be converted to '/'
                };
                jest.spyOn(programmingExerciseService, 'getCheckoutDirectoriesForProgrammingLanguage').mockReturnValue(of(checkoutDirectories));
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
});
