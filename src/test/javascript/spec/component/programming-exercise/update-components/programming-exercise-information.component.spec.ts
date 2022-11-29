import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

describe('ProgrammingExerciseInformationComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInformationComponent>;
    let comp: ProgrammingExerciseInformationComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseInformationComponent, DefaultValueAccessor, NgModel, MockPipe(ArtemisTranslatePipe), MockPipe(RemoveKeysPipe)],
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
