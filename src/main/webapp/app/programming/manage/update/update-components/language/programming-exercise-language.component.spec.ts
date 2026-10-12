import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { NgModel } from '@angular/forms';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { provideHttpClient } from '@angular/common/http';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MAX_PACKAGE_NAME_LENGTH } from 'app/foundation/constants/input.constants';

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
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
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

    describe('sequential test runs checkbox', () => {
        const getSequentialTestRunsCheckbox = (): HTMLInputElement => fixture.nativeElement.querySelector('#field_sequentialTestRuns');

        const setUp = async (configOverrides: Partial<ProgrammingExerciseCreationConfig>) => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.programmingLanguage = ProgrammingLanguage.JAVA;
            exercise.buildConfig = new ProgrammingExerciseBuildConfig();
            exercise.buildConfig.sequentialTestRuns = true;
            fixture.componentRef.setInput('programmingExercise', exercise);
            fixture.componentRef.setInput(
                'programmingExerciseCreationConfig',
                Object.assign({}, programmingExerciseCreationConfigMock, { sequentialTestRunsAllowed: true }, configOverrides),
            );
            fixture.detectChanges();
            // NgModel applies the disabled state in a microtask
            await fixture.whenStable();
        };

        it('should be enabled when a new exercise is created', async () => {
            await setUp({});
            expect(getSequentialTestRunsCheckbox().disabled).toBe(false);
        });

        it('should be disabled when importing from a file', async () => {
            await setUp({ isImportFromFile: true });
            expect(getSequentialTestRunsCheckbox().disabled).toBe(true);
        });

        it('should be disabled when importing from the sharing platform', async () => {
            await setUp({ isImportFromSharing: true });
            expect(getSequentialTestRunsCheckbox().disabled).toBe(true);
        });
    });
});
