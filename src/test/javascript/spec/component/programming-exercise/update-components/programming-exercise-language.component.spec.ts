import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseLanguageComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-language.component';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ProgrammingExerciseTheiaComponent } from 'app/exercises/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { provideHttpClient } from '@angular/common/http';
import { TheiaService } from 'app/exercises/programming/shared/service/theia.service';

describe('ProgrammingExerciseLanguageComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseLanguageComponent>;
    let comp: ProgrammingExerciseLanguageComponent;

    let theiaServiceMock!: { getTheiaImages: jest.Mock };

    beforeEach(() => {
        theiaServiceMock = {
            getTheiaImages: jest.fn(),
        };
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExerciseLanguageComponent,
                ProgrammingExerciseTheiaComponent,
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                NgModel,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveKeysPipe),
            ],
            providers: [
                provideHttpClient(),
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
                {
                    provide: TheiaService,
                    useValue: theiaServiceMock,
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseLanguageComponent);
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
        tick();
        expect(comp).not.toBeNull();
    }));

    it('should not load TheiaComponent when online IDE is not allowed', fakeAsync(() => {
        comp.programmingExercise.allowOnlineIde = false;
        fixture.detectChanges();
        tick();
        expect(comp.programmingExerciseTheiaComponent).toBeUndefined();
    }));

    it('should load TheiaComponent when online IDE is allowed', fakeAsync(() => {
        theiaServiceMock.getTheiaImages.mockReturnValue(of({}));
        comp.programmingExercise.allowOnlineIde = true;
        fixture.detectChanges();
        tick();
        expect(comp.programmingExerciseTheiaComponent).not.toBeNull();
    }));
});
