import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';

import { ProgrammingExerciseUpdateWizardBottomBarComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-bottom-bar.component';
import { ProgrammingExerciseUpdateWizardStepComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-step.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('ProgrammingExerciseWizardBottomBarComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardBottomBarComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardBottomBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExerciseUpdateWizardBottomBarComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(ProgrammingExerciseUpdateWizardStepComponent),
                MockDirective(TranslateDirective),
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
                wizardComponentFixture = TestBed.createComponent(ProgrammingExerciseUpdateWizardBottomBarComponent);
                wizardComponent = wizardComponentFixture.componentInstance;

                wizardComponent.getInvalidReasons = () => [];
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

    it('should return is completed for the right step', fakeAsync(() => {
        wizardComponent.currentStep = 2;
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            const result = wizardComponent.isCompleted(1);
            expect(result).toBeTrue();
        });
    }));

    it('should return is current for the right step', fakeAsync(() => {
        wizardComponent.currentStep = 2;
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            const result = wizardComponent.isCurrentStep(2);
            expect(result).toBeTrue();
        });
    }));

    it('should return correct icon for a step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 3;
            const result = wizardComponent.getNextIcon();
            expect(result).toBe(wizardComponent.faArrowRight);
        });
    }));

    it('should return correct text for a step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 3;
            const result = wizardComponent.getNextText();
            expect(result).toBe('artemisApp.programmingExercise.home.nextStepLabel');
        });
    }));
});
