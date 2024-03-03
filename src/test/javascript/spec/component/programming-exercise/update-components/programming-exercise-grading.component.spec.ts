import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseGradingComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-grading.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CheckboxControlValueAccessor, DefaultValueAccessor, NgModel, NumberValueAccessor, SelectControlValueAccessor } from '@angular/forms';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { PresentationScoreComponent } from 'app/exercises/shared/presentation-score/presentation-score.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { NgbAlertsMocksModule } from '../../../helpers/mocks/directive/ngbAlertsMocks.module';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';

describe('ProgrammingExerciseGradingComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseGradingComponent>;
    let comp: ProgrammingExerciseGradingComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbAlertsMocksModule, MockDirective(NgbTooltip), MockDirective(NgbCollapse)],
            declarations: [
                ProgrammingExerciseGradingComponent,
                NgModel,
                CheckboxControlValueAccessor,
                DefaultValueAccessor,
                SelectControlValueAccessor,
                NumberValueAccessor,
                MockDirective(CustomMinDirective),
                MockDirective(CustomMaxDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HelpIconComponent),
                MockComponent(ProgrammingExerciseLifecycleComponent),
                MockComponent(IncludedInOverallScorePickerComponent),
                MockComponent(SubmissionPolicyUpdateComponent),
                MockComponent(PresentationScoreComponent),
                MockComponent(FaIconComponent),
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

                comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;

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

    it('should create a grading summary for a bonus exercise with semiautomatic assessment', fakeAsync(() => {
        fixture.detectChanges();

        comp.programmingExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
        comp.programmingExercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        comp.programmingExercise.bonusPoints = undefined;

        fixture.whenStable().then(() => {
            const result = comp.getGradingSummary();
            expect(result).not.toBe('');
        });
    }));

    it('should create a grading summary with exceeding penalty', fakeAsync(() => {
        fixture.detectChanges();

        comp.programmingExercise.submissionPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY, exceedingPenalty: 10, submissionLimit: 5 };
        comp.programmingExercise.maxStaticCodeAnalysisPenalty = 5;

        fixture.whenStable().then(() => {
            const result = comp.getGradingSummary();
            expect(result).not.toBe('');
        });
    }));

    it('should create a grading summary with locked repositories and disabled code analysis', fakeAsync(() => {
        fixture.detectChanges();

        comp.programmingExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 };
        comp.programmingExercise.staticCodeAnalysisEnabled = false;

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

    it('should return replacement for grading summary key', fakeAsync(() => {
        fixture.detectChanges();

        const replacements = {
            exerciseType: 'replacedType',
        };

        const replacedString = comp.replacePlaceholder('"exerciseType"', 'exerciseType', replacements);

        expect(replacedString).toBe('replacedType');
    }));

    it('should not return replacement for unknown grading summary key', fakeAsync(() => {
        fixture.detectChanges();

        const replacements = {
            exerciseType: 'replacedType',
        };

        const replacedString = comp.replacePlaceholder('"exerciseType2"', 'exerciseType2', replacements);

        expect(replacedString).toBe('"exerciseType2"');
    }));

    it('should update form section calculation', () => {
        const calculateFormStatusSpy = jest.spyOn(comp, 'calculateFormStatus');

        comp.submissionPolicyUpdateComponent = { form: { valueChanges: new Subject() } } as any as SubmissionPolicyUpdateComponent;
        comp.lifecycleComponent = { formValidChanges: new Subject() } as any as ProgrammingExerciseLifecycleComponent;

        comp.ngAfterViewInit();

        (comp.submissionPolicyUpdateComponent.form.valueChanges as Subject<boolean>).next(false);
        comp.lifecycleComponent.formValidChanges.next(false);

        expect(calculateFormStatusSpy).toHaveBeenCalledTimes(2);
    });
});
