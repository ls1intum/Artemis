import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProvider } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';

import { ProgrammingExerciseInformationComponent } from 'app/programming/manage/update/update-components/information/programming-exercise-information.component';
import { NgModel } from '@angular/forms';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { TableEditableFieldComponent } from 'app/shared-ui/table/editable-field/table-editable-field.component';
import { Signal, signal } from '@angular/core';
import { ProgrammingExerciseEditCheckoutDirectoriesComponent } from 'app/programming/shared/build-details/programming-exercise-edit-checkout-directories/programming-exercise-edit-checkout-directories.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

/**
 * Typed view onto the component's view-query signals so the spec can stub the child components/form
 * controls that would otherwise only become available after a real render, without a blanket
 * `(component as any)` cast. Each property mirrors the component's declaration and is reassigned with a
 * plain `signal(...)` (a `WritableSignal` is assignable to the declared `Signal<...>` field).
 */
type InformationInternals = ProgrammingExerciseInformationComponent & {
    checkoutSolutionRepositoryField: Signal<NgModel | undefined>;
    recreateBuildPlansField: Signal<NgModel | undefined>;
    updateTemplateFilesField: Signal<NgModel | undefined>;
    tableEditableFields: Signal<readonly TableEditableFieldComponent[]>;
    programmingExerciseEditCheckoutDirectories: Signal<ProgrammingExerciseEditCheckoutDirectoriesComponent | undefined>;
};
const internals = (c: ProgrammingExerciseInformationComponent): InformationInternals => c as InformationInternals;

describe('ProgrammingExerciseInformationComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseInformationComponent>;
    let comp: ProgrammingExerciseInformationComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
                { provide: ExerciseService, useValue: MockExerciseService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseInformationComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            shortName: true,
            categories: true,
        });
        fixture.componentRef.setInput('isSimpleMode', false);
        fixture.componentRef.setInput('isExamMode', false);
        fixture.componentRef.setInput('isImport', false);
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        comp.programmingExercise().buildConfig = new ProgrammingExerciseBuildConfig();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should should calculate Form Sections correctly', () => {
        const calculateFormValidSpy = vi.spyOn(comp, 'calculateFormValid');
        const editableField = {
            editingInput: {
                valueChanges: new Subject(),
                valid: true,
            },
        } as unknown as TableEditableFieldComponent;
        const checkoutSolutionRepositoryField = { valueChanges: new Subject(), valid: true } as unknown as NgModel;
        const recreateBuildPlansField = { valueChanges: new Subject(), valid: true } as unknown as NgModel;
        const updateTemplateFilesField = { valueChanges: new Subject(), valid: true } as unknown as NgModel;
        const programmingExerciseEditCheckoutDirectories = { formValidChanges: new Subject() } as ProgrammingExerciseEditCheckoutDirectoriesComponent;

        // Stub the view-query signals so registerInputFields() wires up the valueChanges subscriptions.
        internals(comp).checkoutSolutionRepositoryField = signal(checkoutSolutionRepositoryField);
        internals(comp).recreateBuildPlansField = signal(recreateBuildPlansField);
        internals(comp).updateTemplateFilesField = signal(updateTemplateFilesField);
        internals(comp).tableEditableFields = signal([editableField]);
        internals(comp).programmingExerciseEditCheckoutDirectories = signal(programmingExerciseEditCheckoutDirectories);

        comp.ngAfterViewInit();
        (checkoutSolutionRepositoryField.valueChanges as Subject<boolean>).next(false);
        (recreateBuildPlansField.valueChanges as Subject<boolean>).next(false);
        (updateTemplateFilesField.valueChanges as Subject<boolean>).next(false);
        (editableField.editingInput.valueChanges as Subject<boolean>).next(false);
        programmingExerciseEditCheckoutDirectories.formValidChanges.next(false);
        expect(calculateFormValidSpy).toHaveBeenCalledTimes(5);
    });

    it('should update checkout directories', () => {
        comp.onTestRepositoryCheckoutPathChange('test');
        expect(comp.programmingExercise().buildConfig?.testCheckoutPath).toBe('test');
        comp.onSolutionRepositoryCheckoutPathChange('solution');
        expect(comp.programmingExercise().buildConfig?.solutionCheckoutPath).toBe('solution');
        comp.onAssigmentRepositoryCheckoutPathChange('assignment');
        expect(comp.programmingExercise().buildConfig?.assignmentCheckoutPath).toBe('assignment');
    });

    describe('shortName generation effect', () => {
        it('should use name from import', () => {
            comp.programmingExercise().shortName = 'l01e01';
            fixture.componentRef.setInput('isSimpleMode', false);
            fixture.componentRef.setInput('isImport', true);

            comp.programmingExercise().title = 'Test Exercise';
            fixture.changeDetectorRef.detectChanges();

            expect(comp.programmingExercise().shortName).toBe('l01e01');
        });

        it('should derive shortname from title', () => {
            fixture.componentRef.setInput('isSimpleMode', true);

            comp.programmingExercise().title = 'Test Exercise';
            fixture.changeDetectorRef.detectChanges();

            expect(comp.programmingExercise().shortName).toMatch('TestExercise');
        });

        it('should derive shortname from title when directly derived shortname is already taken', () => {
            fixture.componentRef.setInput('isSimpleMode', true);
            comp.alreadyUsedShortNames.set(new Set(['TestExercise']));

            comp.programmingExercise().title = 'Test Exercise';
            fixture.changeDetectorRef.detectChanges();

            expect(comp.programmingExercise().shortName).toMatch('TestExercise1');
        });

        it('should truncate auto-generated short names to PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH', () => {
            fixture.componentRef.setInput('isSimpleMode', true);

            // 50-char title sanitises to 50 chars, must be truncated to 36.
            comp.programmingExercise().title = 'A'.repeat(50);
            fixture.changeDetectorRef.detectChanges();

            expect(comp.programmingExercise().shortName!).toHaveLength(36);
            expect(comp.programmingExercise().shortName).toBe('A'.repeat(36));
        });

        it('should truncate the base when adding a uniqueness suffix would exceed the max length', () => {
            fixture.componentRef.setInput('isSimpleMode', true);
            // Pre-load the truncated 36-char base so the generator must add a numeric suffix while keeping length <= 36.
            comp.alreadyUsedShortNames.set(new Set(['A'.repeat(36)]));

            comp.programmingExercise().title = 'A'.repeat(50);
            fixture.changeDetectorRef.detectChanges();

            expect(comp.programmingExercise().shortName!).toHaveLength(36);
            expect(comp.programmingExercise().shortName).toBe('A'.repeat(35) + '1');
        });
    });
});
