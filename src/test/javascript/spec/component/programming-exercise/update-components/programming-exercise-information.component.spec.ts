import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ExerciseTitleChannelNameComponent } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';

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

    it('should should calculate Form Sections correctly', () => {
        const calculateFormValidSpy = jest.spyOn(comp, 'calculateFormValid');
        comp.exerciseTitleChannelComponent = { titleChannelNameComponent: { formValidChanges: new Subject(), formValid: true } } as ExerciseTitleChannelNameComponent;
        comp.ngAfterViewInit();
        comp.exerciseTitleChannelComponent.titleChannelNameComponent.formValidChanges.next(false);
        expect(calculateFormValidSpy).toHaveBeenCalledOnce();
    });
});
