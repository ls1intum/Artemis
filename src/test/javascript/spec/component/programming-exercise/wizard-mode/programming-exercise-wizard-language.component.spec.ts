import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseUpdateWizardLanguageComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-language.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ProgrammingExerciseLanguageComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-language.component';

describe('ProgrammingExerciseWizardLanguageComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardLanguageComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardLanguageComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseUpdateWizardLanguageComponent, MockComponent(ProgrammingExerciseLanguageComponent), MockPipe(ArtemisTranslatePipe)],
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
                wizardComponentFixture = TestBed.createComponent(ProgrammingExerciseUpdateWizardLanguageComponent);
                wizardComponent = wizardComponentFixture.componentInstance;

                const exercise = new ProgrammingExercise(undefined, undefined);
                exercise.maxPoints = 10;
                exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                exercise.assessmentType = AssessmentType.AUTOMATIC;
                exercise.submissionPolicy = { type: SubmissionPolicyType.NONE };

                wizardComponent.programmingExercise = exercise;
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
