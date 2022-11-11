import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseUpdateWizardComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

describe('ProgrammingExerciseWizardComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseUpdateWizardComponent, MockPipe(ArtemisTranslatePipe)],
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
