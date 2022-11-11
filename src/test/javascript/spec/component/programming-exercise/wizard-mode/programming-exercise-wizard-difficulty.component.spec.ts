import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseUpdateWizardDifficultyComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-difficulty.component';

describe('ProgrammingExerciseWizardDifficultyComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardDifficultyComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardDifficultyComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseUpdateWizardDifficultyComponent, MockPipe(ArtemisTranslatePipe)],
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
                wizardComponentFixture = TestBed.createComponent(ProgrammingExerciseUpdateWizardDifficultyComponent);
                wizardComponent = wizardComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();
    }));
});
