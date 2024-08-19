import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-difficulty.component';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';
import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';
import { TeamConfigFormGroupComponent } from 'app/exercises/shared/team-config-form-group/team-config-form-group.component';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseBuildDetailsComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-build-details.component';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { QueryList } from '@angular/core';

describe('ProgrammingExerciseBuildDetailsComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseBuildDetailsComponent>;
    let comp: ProgrammingExerciseBuildDetailsComponent;

    beforeEach(() => {
        let NgxDatatableModule;
        TestBed.configureTestingModule({
            imports: [MockModule(ArtemisSharedModule), MockModule(ArtemisSharedComponentModule), MockModule(ArtemisTableModule), NgxDatatableModule],
            declarations: [
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                NgModel,
                ProgrammingExerciseDifficultyComponent,
                MockComponent(DifficultyPickerComponent),
                MockComponent(TeamConfigFormGroupComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(AddAuxiliaryRepositoryButtonComponent),
                MockComponent(RemoveAuxiliaryRepositoryButtonComponent),
                MockComponent(ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent),
                MockComponent(ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent),
                ProgrammingExerciseBuildDetailsComponent,
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
                fixture = TestBed.createComponent(ProgrammingExerciseBuildDetailsComponent);
                comp = fixture.componentInstance;
                comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
                comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should should calculate Form Sections correctly', () => {
        const calculateFormValidSpy = jest.spyOn(comp, 'calculateFormValid');
        const editableField = { editingInput: { valueChanges: new Subject(), valid: true } } as any as TableEditableFieldComponent;
        comp.shortNameField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.checkoutSolutionRepositoryField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.recreateBuildPlansField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.updateTemplateFilesField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.tableEditableFields = { changes: new Subject<any>() } as any as QueryList<TableEditableFieldComponent>;
        comp.ngAfterViewInit();
        (comp.tableEditableFields?.changes as Subject<any>).next({ toArray: () => [editableField] } as any as QueryList<TableEditableFieldComponent>);
        (comp.shortNameField.valueChanges as Subject<boolean>).next(false);
        (comp.checkoutSolutionRepositoryField.valueChanges as Subject<boolean>).next(false);
        (comp.recreateBuildPlansField.valueChanges as Subject<boolean>).next(false);
        (comp.updateTemplateFilesField.valueChanges as Subject<boolean>).next(false);
        (editableField.editingInput.valueChanges as Subject<boolean>).next(false);
        expect(calculateFormValidSpy).toHaveBeenCalledTimes(5);
    });
});
