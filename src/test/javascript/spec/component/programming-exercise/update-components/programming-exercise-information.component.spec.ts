import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/information/programming-exercise-information.component';
import { NgModel } from '@angular/forms';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/entities/programming/programming-exercise-build.config';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { QueryList } from '@angular/core';
import { ProgrammingExerciseEditCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-edit-checkout-directories/programming-exercise-edit-checkout-directories.component';
import { ExerciseService } from '../../../../../../main/webapp/app/exercises/shared/exercise/exercise.service';
import { MockExerciseService } from '../../../helpers/mocks/service/mock-exercise.service';
import { AlertService } from '../../../../../../main/webapp/app/core/util/alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ProgrammingExerciseInformationComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInformationComponent>;
    let comp: ProgrammingExerciseInformationComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [BrowserAnimationsModule],
            providers: [
                MockProvider(AlertService),
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: ExerciseService,
                    useValue: MockExerciseService,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
            schemas: [],
        }).compileComponents();
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
        fixture.componentRef.setInput('isLocal', false);
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        comp.programmingExercise().buildConfig = new ProgrammingExerciseBuildConfig();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    }));

    it('should should calculate Form Sections correctly', () => {
        const calculateFormValidSpy = jest.spyOn(comp, 'calculateFormValid');
        const editableField = {
            editingInput: {
                valueChanges: new Subject(),
                valid: true,
            },
        } as unknown as TableEditableFieldComponent;
        comp.checkoutSolutionRepositoryField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.recreateBuildPlansField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.updateTemplateFilesField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.tableEditableFields = { changes: new Subject<any>() } as any as QueryList<TableEditableFieldComponent>;
        comp.programmingExerciseEditCheckoutDirectories = { formValidChanges: new Subject() } as ProgrammingExerciseEditCheckoutDirectoriesComponent;
        comp.ngAfterViewInit();
        (comp.tableEditableFields.changes as Subject<any>).next({ toArray: () => [editableField] } as any as QueryList<TableEditableFieldComponent>);
        (comp.checkoutSolutionRepositoryField.valueChanges as Subject<boolean>).next(false);
        (comp.recreateBuildPlansField.valueChanges as Subject<boolean>).next(false);
        (comp.updateTemplateFilesField.valueChanges as Subject<boolean>).next(false);
        (editableField.editingInput.valueChanges as Subject<boolean>).next(false);
        comp.programmingExerciseEditCheckoutDirectories.formValidChanges.next(false);
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
            fixture.detectChanges();

            expect(comp.programmingExercise().shortName).toBe('l01e01');
        });

        it('should derive shortname from title', () => {
            fixture.componentRef.setInput('isSimpleMode', true);

            comp.programmingExercise().title = 'Test Exercise';
            fixture.detectChanges();

            expect(comp.programmingExercise().shortName).toMatch('TestExercise');
        });

        it('should derive shortname from title when directly derived shortname is already taken', () => {
            fixture.componentRef.setInput('isSimpleMode', true);
            comp.alreadyUsedShortNames.set(new Set(['TestExercise']));

            comp.programmingExercise().title = 'Test Exercise';
            fixture.detectChanges();

            expect(comp.programmingExercise().shortName).toMatch('TestExercise1');
        });
    });
});
