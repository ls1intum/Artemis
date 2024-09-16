import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/information/programming-exercise-information.component';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { QueryList } from '@angular/core';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';

describe('ProgrammingExerciseInformationComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInformationComponent>;
    let comp: ProgrammingExerciseInformationComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExerciseInformationComponent,
                DefaultValueAccessor,
                NgModel,
                MockComponent(HelpIconComponent),
                MockComponent(ExerciseTitleChannelNameComponent),
                MockComponent(ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent),
                MockComponent(CategorySelectorComponent),
                MockComponent(AddAuxiliaryRepositoryButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveKeysPipe),
                MockModule(ArtemisProgrammingExerciseUpdateModule),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInformationComponent);
                comp = fixture.componentInstance;

                comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;

                fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
                fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
                    shortName: true,
                    categories: true,
                });
                fixture.componentRef.setInput('isSimpleMode', false);
                fixture.componentRef.setInput('isExamMode', false);
                fixture.componentRef.setInput('isImport', false);
            });
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
        comp.ngAfterViewInit();
        (comp.tableEditableFields.changes as Subject<any>).next({ toArray: () => [editableField] } as any as QueryList<TableEditableFieldComponent>);
        (comp.checkoutSolutionRepositoryField.valueChanges as Subject<boolean>).next(false);
        (comp.recreateBuildPlansField.valueChanges as Subject<boolean>).next(false);
        (comp.updateTemplateFilesField.valueChanges as Subject<boolean>).next(false);
        (editableField.editingInput.valueChanges as Subject<boolean>).next(false);
        expect(calculateFormValidSpy).toHaveBeenCalledTimes(4);
    });

    describe('shortName generation effect', () => {
        it('should use name from import', () => {
            comp.programmingExercise().shortName = 'l01e01';
            fixture.componentRef.setInput('isSimpleMode', true);
            fixture.componentRef.setInput('isImport', true);

            comp.programmingExercise().title = 'Test Exercise';
            fixture.detectChanges();

            expect(comp.programmingExercise().shortName).toBe('l01e01');
        });

        it('should derive name from title', () => {
            fixture.componentRef.setInput('isSimpleMode', true);

            comp.programmingExercise().title = 'Test Exercise';
            fixture.detectChanges();

            expect(comp.programmingExercise().shortName).toMatch(/^TestEx\w{3}$/);
        });
    });
});
