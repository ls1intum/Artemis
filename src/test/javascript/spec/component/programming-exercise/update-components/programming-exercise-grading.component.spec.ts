import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseGradingComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-grading.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';

describe('ProgrammingExerciseGradingComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseGradingComponent>;
    let comp: ProgrammingExerciseGradingComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                ProgrammingExerciseGradingComponent,
                NgModel,
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                MockPipe(ArtemisTranslatePipe),
            ],
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
                fixture = TestBed.createComponent(ProgrammingExerciseGradingComponent);
                comp = fixture.componentInstance;

                comp.gradingInputs = {
                    staticCodeAnalysisAllowed: true,
                    onStaticCodeAnalysisChanged(): void {},
                    maxPenaltyPattern: '',
                };

                const exercise = new ProgrammingExercise(undefined, undefined);
                exercise.maxPoints = 10;
                exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                exercise.assessmentType = AssessmentType.AUTOMATIC;
                exercise.submissionPolicy = { type: SubmissionPolicyType.NONE };
                exercise.staticCodeAnalysisEnabled = true;

                comp.programmingExercise = exercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    }));

    it('should create a grading summary', fakeAsync(() => {
        fixture.detectChanges();

        fixture.whenStable().then(() => {
            const result = comp.getGradingSummary();
            expect(result).not.toBe('');
        });
    }));

    it('should not create a grading summary when there are no points', fakeAsync(() => {
        fixture.detectChanges();

        comp.programmingExercise.maxPoints = undefined;

        fixture.whenStable().then(() => {
            const result = comp.getGradingSummary();
            expect(result).toBe('');
        });
    }));
});
