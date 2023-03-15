import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';

import { AssessmentType } from 'app/entities/assessment-type.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { ProgrammingExerciseUpdateWizardInformationComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-information.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';

describe('ProgrammingExerciseWizardInformationComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardInformationComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardInformationComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExerciseUpdateWizardInformationComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ProgrammingExerciseInformationComponent),
                DefaultValueAccessor,
                NgModel,
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
                wizardComponentFixture = TestBed.createComponent(ProgrammingExerciseUpdateWizardInformationComponent);
                wizardComponent = wizardComponentFixture.componentInstance;

                const exercise = new ProgrammingExercise(undefined, undefined);
                exercise.title = 'Title';
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

    it('should initialize', () => {
        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();
    });
});
