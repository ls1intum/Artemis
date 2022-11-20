import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseUpdateWizardGradingComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-grading.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { DefaultValueAccessor, NgModel } from '@angular/forms';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseWizardGradingComponent', () => {
    let wizardComponentFixture: ComponentFixture<ProgrammingExerciseUpdateWizardGradingComponent>;
    let wizardComponent: ProgrammingExerciseUpdateWizardGradingComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ProgrammingExerciseUpdateWizardGradingComponent, MockPipe(ArtemisTranslatePipe), DefaultValueAccessor, NgModel, MockPipe(RemoveKeysPipe)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardComponentFixture = TestBed.createComponent(ProgrammingExerciseUpdateWizardGradingComponent);
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

    it('should create a grading summary', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            const result = wizardComponent.getGradingSummary();
            expect(result).not.toBe('');
        });
    }));
});
