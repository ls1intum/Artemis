import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { QueryList } from '@angular/core';

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
                MockComponent(ProgrammingExercisePlansAndRepositoriesPreviewComponent),
                MockComponent(CategorySelectorComponent),
                MockComponent(AddAuxiliaryRepositoryButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveKeysPipe),
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

                comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
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
        const editableField = { editingInput: { valueChanges: new Subject(), valid: true } } as any as TableEditableFieldComponent;
        comp.exerciseTitleChannelComponent = { titleChannelNameComponent: { formValidChanges: new Subject(), formValid: true } } as ExerciseTitleChannelNameComponent;
        comp.shortNameField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.checkoutSolutionRepositoryField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.recreateBuildPlansField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.updateTemplateFilesField = { valueChanges: new Subject(), valid: true } as any as NgModel;
        comp.tableEditableFields = { changes: new Subject<any>() } as any as QueryList<TableEditableFieldComponent>;
        comp.ngAfterViewInit();
        (comp.tableEditableFields.changes as Subject<any>).next({ toArray: () => [editableField] } as any as QueryList<TableEditableFieldComponent>);
        comp.exerciseTitleChannelComponent.titleChannelNameComponent.formValidChanges.next(false);
        (comp.shortNameField.valueChanges as Subject<boolean>).next(false);
        (comp.checkoutSolutionRepositoryField.valueChanges as Subject<boolean>).next(false);
        (comp.recreateBuildPlansField.valueChanges as Subject<boolean>).next(false);
        (comp.updateTemplateFilesField.valueChanges as Subject<boolean>).next(false);
        (editableField.editingInput.valueChanges as Subject<boolean>).next(false);
        expect(calculateFormValidSpy).toHaveBeenCalledTimes(6);
    });
});
