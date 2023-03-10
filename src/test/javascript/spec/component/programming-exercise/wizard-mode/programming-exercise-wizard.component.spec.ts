import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseUpdateWizardComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseUpdateWizardBottomBarComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-bottom-bar.component';

describe('ProgrammingExerciseWizardComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseUpdateWizardComponent, MockComponent(ProgrammingExerciseUpdateWizardBottomBarComponent), MockPipe(ArtemisTranslatePipe)],
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
                wizardComponentFixture = TestBed.createComponent(ProgrammingExerciseUpdateWizardComponent);
                wizardComponent = wizardComponentFixture.componentInstance;

                const exercise = new ProgrammingExercise(undefined, undefined);
                wizardComponent.exercise = exercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();
    }));

    it('should emit an event when clicking next', fakeAsync(() => {
        const saveStub = jest.spyOn(wizardComponent.onNextStep, 'emit');

        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.nextStep();
            expect(saveStub).toHaveBeenCalledOnce();
        });
    }));
});
