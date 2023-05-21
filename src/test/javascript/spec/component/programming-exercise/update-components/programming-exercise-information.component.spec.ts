import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';

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
                MockComponent(TitleChannelNameComponent),
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
                    updateCategories(): void {},
                    updateCheckoutDirectory(): (newValue: any) => string | undefined {
                        return function () {
                            return undefined;
                        };
                    },
                    updateRepositoryName(): (newValue: any) => string | undefined {
                        return function () {
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
