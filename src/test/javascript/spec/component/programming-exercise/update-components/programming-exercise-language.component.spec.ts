import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MockPipe } from 'ng-mocks';
import { of } from 'rxjs';

import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseLanguageComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-language.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';

describe('ProgrammingExerciseLanguageComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseLanguageComponent>;
    let comp: ProgrammingExerciseLanguageComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExerciseLanguageComponent,
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                NgModel,
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
                fixture = TestBed.createComponent(ProgrammingExerciseLanguageComponent);
                comp = fixture.componentInstance;

                comp.languageStepInputs = {
                    modePickerOptions: [],
                    onProgrammingLanguageChange(language: ProgrammingLanguage): ProgrammingLanguage {
                        return ProgrammingLanguage.EMPTY;
                    },
                    onProjectTypeChange(projectType: ProjectType): ProjectType {
                        return ProjectType.PLAIN;
                    },
                    packageNamePattern: '',
                    packageNameRequired: false,
                    projectTypes: [],
                    selectedProgrammingLanguage: ProgrammingLanguage.OCAML,
                    selectedProjectType: ProjectType.FACT,
                    supportedLanguages: [],
                    withDependencies: false,
                    onWithDependenciesChanged(withDependencies: boolean): boolean {
                        return false;
                    },
                    appNamePatternForSwift: '',
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
