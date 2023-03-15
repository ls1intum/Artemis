import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';

import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';

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

                comp.infoInputs = {
                    auxiliaryRepositoryDuplicateDirectories: false,
                    auxiliaryRepositoryDuplicateNames: false,
                    exerciseCategories: [],
                    existingCategories: [],
                    invalidDirectoryNamePattern: new RegExp(''),
                    invalidRepositoryNamePattern: new RegExp(''),
                    refreshAuxiliaryRepositoryChecks(): void {},
                    shortNamePattern: new RegExp(''),
                    updateCategories(categories: ExerciseCategory[]): void {},
                    updateCheckoutDirectory(editedAuxiliaryRepository: AuxiliaryRepository): (newValue: any) => string | undefined {
                        return function (p1: any) {
                            return undefined;
                        };
                    },
                    updateRepositoryName(auxiliaryRepository: AuxiliaryRepository): (newValue: any) => string | undefined {
                        return function (p1: any) {
                            return undefined;
                        };
                    },
                    titleNamePattern: '',
                };

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
});
