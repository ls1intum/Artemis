import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { NgModel } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { provideHttpClient } from '@angular/common/http';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MAX_PACKAGE_NAME_LENGTH } from 'app/foundation/constants/input.constants';
import { PROFILE_HADES } from 'app/app.constants';

/**
 * Typed view onto the `packageNameField` viewChild signal so the spec can stub it directly
 * instead of depending on real NgModel validity timing in the zoneless test environment.
 */
type LanguageInternals = ProgrammingExerciseLanguageComponent & {
    packageNameField: Signal<NgModel | undefined>;
};
const internals = (c: ProgrammingExerciseLanguageComponent): LanguageInternals => c as LanguageInternals;

describe('ProgrammingExerciseLanguageComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseLanguageComponent>;
    let comp: ProgrammingExerciseLanguageComponent;

    let theiaServiceMock!: { getTheiaImages: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        theiaServiceMock = {
            getTheiaImages: vi.fn(),
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
        });
        fixture = TestBed.createComponent(ProgrammingExerciseLanguageComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
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

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should validate only the docker image field for Hades custom build plans', () => {
        comp.programmingExercise().customizeBuildPlan = true;
        fixture.componentRef.setInput('programmingExerciseCreationConfig', Object.assign({}, programmingExerciseCreationConfigMock, { customBuildPlansSupported: PROFILE_HADES }));

        const stub = (dockerImageValid: boolean) =>
            ((comp as unknown as { programmingExerciseCustomBuildPlanComponent: unknown }).programmingExerciseCustomBuildPlanComponent = () => ({
                programmingExerciseDockerImageComponent: () => ({ dockerImageField: () => ({ valid: dockerImageValid }) }),
            }));

        stub(true);
        expect(comp.isCustomBuildPlanValid()).toBe(true);

        stub(false);
        expect(comp.isCustomBuildPlanValid()).toBe(false);
    });

    it('should not load TheiaComponent when online IDE is not allowed', () => {
        comp.programmingExercise().allowOnlineIde = false;
        fixture.detectChanges();
        expect(comp.programmingExerciseTheiaComponent()).toBeUndefined();
    });

    it('should load TheiaComponent when online IDE is allowed', () => {
        theiaServiceMock.getTheiaImages.mockReturnValue(of({}));
        comp.programmingExercise().allowOnlineIde = true;
        fixture.detectChanges();
        expect(comp.programmingExerciseTheiaComponent()).toBeDefined();
    });

    it('should mark package name as invalid when it exceeds the maximum length', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.packageName = 'a'.repeat(MAX_PACKAGE_NAME_LENGTH + 1);
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('programmingExerciseCreationConfig', Object.assign({}, programmingExerciseCreationConfigMock, { packageNameRequired: true }));
        fixture.detectChanges();
        const packageNameInput: HTMLInputElement = fixture.nativeElement.querySelector('#field_packageName');
        expect(packageNameInput.maxLength).toBe(MAX_PACKAGE_NAME_LENGTH);
        vi.spyOn(internals(comp), 'packageNameField').mockReturnValue({ valid: false } as NgModel);
        expect(comp.isPackageNameValid()).toBe(false);
    });

    it('should mark package name as valid when it is within the maximum length', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.packageName = 'validpackage';
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.componentRef.setInput('programmingExerciseCreationConfig', Object.assign({}, programmingExerciseCreationConfigMock, { packageNameRequired: true }));
        fixture.detectChanges();
        const packageNameInput: HTMLInputElement = fixture.nativeElement.querySelector('#field_packageName');
        expect(packageNameInput.maxLength).toBe(MAX_PACKAGE_NAME_LENGTH);
        vi.spyOn(internals(comp), 'packageNameField').mockReturnValue({ valid: true } as NgModel);
        expect(comp.isPackageNameValid()).toBe(true);
    });
});
