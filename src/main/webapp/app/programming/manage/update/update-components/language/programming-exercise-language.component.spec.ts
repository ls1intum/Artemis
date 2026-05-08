import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { provideHttpClient } from '@angular/common/http';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MAX_PACKAGE_NAME_LENGTH } from 'app/shared/constants/input.constants';

describe('ProgrammingExerciseLanguageComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseLanguageComponent>;
    let comp: ProgrammingExerciseLanguageComponent;

    let theiaServiceMock!: { getTheiaImages: jest.Mock };

    beforeEach(() => {
        theiaServiceMock = {
            getTheiaImages: jest.fn(),
        };
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: TheiaService,
                    useValue: theiaServiceMock,
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseLanguageComponent);
                comp = fixture.componentInstance;
                comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
                comp.programmingExercise = new ProgrammingExercise(undefined, undefined);

                fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
                    programmingLanguage: true,
                    projectType: true,
                    withExemplaryDependency: true,
                    packageName: true,
                    enableStaticCodeAnalysis: true,
                    sequentialTestRuns: true,
                    customizeBuildScript: true,
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        tick();
        expect(comp).not.toBeNull();
    }));

    it('should not load TheiaComponent when online IDE is not allowed', fakeAsync(() => {
        comp.programmingExercise.allowOnlineIde = false;
        fixture.detectChanges();
        tick();
        expect(comp.programmingExerciseTheiaComponent).toBeUndefined();
    }));

    it('should load TheiaComponent when online IDE is allowed', fakeAsync(() => {
        theiaServiceMock.getTheiaImages.mockReturnValue(of({}));
        comp.programmingExercise.allowOnlineIde = true;
        fixture.detectChanges();
        tick();
        expect(comp.programmingExerciseTheiaComponent).not.toBeNull();
    }));

    it('should mark package name as invalid when it exceeds the maximum length', fakeAsync(() => {
        comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        comp.programmingExerciseCreationConfig = { ...programmingExerciseCreationConfigMock, packageNameRequired: true };
        comp.programmingExercise.packageName = 'a'.repeat(MAX_PACKAGE_NAME_LENGTH + 1);
        fixture.detectChanges();
        tick();
        expect(comp.isPackageNameValid()).toBeFalse();
    }));

    it('should mark package name as valid when it is within the maximum length', fakeAsync(() => {
        comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        comp.programmingExerciseCreationConfig = { ...programmingExerciseCreationConfigMock, packageNameRequired: true };
        comp.programmingExercise.packageName = 'validpackage';
        fixture.detectChanges();
        tick();
        expect(comp.isPackageNameValid()).toBeTrue();
    }));
});
