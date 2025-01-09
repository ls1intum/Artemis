import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ProgrammingExerciseGradingComponent } from 'app/exercises/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { programmingExerciseCreationConfigMock } from './programming-exercise-creation-config-mock';
import { ProgrammingExerciseInputField } from 'app/exercises/programming/manage/update/programming-exercise-update.helper';
import { ArtemisTestModule } from '../../../test.module';

describe('ProgrammingExerciseGradingComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseGradingComponent>;
    let comp: ProgrammingExerciseGradingComponent;

    const route = {
        queryParams: of({}),
        url: {
            pipe: () => ({
                subscribe: () => {},
            }),
        },
    } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockDirective(NgbTooltip), MockDirective(NgbCollapse)],

            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
            ],
            schemas: [],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseGradingComponent);
        comp = fixture.componentInstance;

        comp.programmingExerciseCreationConfig = programmingExerciseCreationConfigMock;
        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            includeExerciseInCourseScoreCalculation: true,
            points: true,
            bonusPoints: true,
            submissionPolicy: true,
            timeline: true,
            assessmentInstructions: true,
            presentationScore: true,
        });

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.maxPoints = 10;
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.submissionPolicy = { type: SubmissionPolicyType.NONE };
        exercise.staticCodeAnalysisEnabled = true;

        comp.programmingExercise = exercise;
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

        comp.programmingExercise.submissionPolicy = {
            type: SubmissionPolicyType.SUBMISSION_PENALTY,
            exceedingPenalty: 10,
            submissionLimit: 5,
        };
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

        comp.ngAfterContentInit();

        (comp.submissionPolicyUpdateComponent.form.valueChanges as Subject<boolean>).next(false);
        comp.lifecycleComponent.formValidChanges.next(false);

        expect(calculateFormStatusSpy).toHaveBeenCalledTimes(2);
    });

    const generateFieldVisibilityTests = (
        testCases: {
            name: string;
            selector: string;
            field: ProgrammingExerciseInputField;
            extraCondition?: () => void;
        }[],
    ) => {
        const checkFieldVisibility = (selector: string, isVisible: boolean) => {
            fixture.detectChanges();
            const field = fixture.debugElement.nativeElement.querySelector(selector);
            if (isVisible) {
                expect(field).not.toBeNull();
            } else {
                expect(field).toBeNull();
            }
        };

        testCases.forEach(({ name, selector, field, extraCondition }) => {
            describe('should handle input field ' + name + ' properly', () => {
                it('should be displayed', () => {
                    fixture.detectChanges();
                    extraCondition?.();
                    checkFieldVisibility(selector, true);
                });

                it('should NOT be displayed', () => {
                    fixture.detectChanges();
                    extraCondition?.();
                    comp.isEditFieldDisplayedRecord()[field] = false;
                    checkFieldVisibility(selector, false);
                });
            });
        });
    };

    describe('should handle field visibility', () => {
        const testCases: {
            name: string;
            selector: string;
            field: ProgrammingExerciseInputField;
            extraCondition?: () => void;
        }[] = [
            {
                name: 'jhi-included-in-overall-score-picker',
                selector: 'jhi-included-in-overall-score-picker',
                field: ProgrammingExerciseInputField.INCLUDE_EXERCISE_IN_COURSE_SCORE_CALCULATION,
            },
            { name: 'points field', selector: '#field_points', field: ProgrammingExerciseInputField.POINTS },
            {
                name: 'bonusPoints field',
                selector: '#field_bonusPoints',
                field: ProgrammingExerciseInputField.BONUS_POINTS,
            },
            {
                name: 'submission policy field',
                selector: 'jhi-submission-policy-update',
                field: ProgrammingExerciseInputField.SUBMISSION_POLICY,
            },
            {
                name: 'timeline',
                selector: 'jhi-programming-exercise-lifecycle',
                field: ProgrammingExerciseInputField.TIMELINE,
            },
            {
                name: 'assessment instructions',
                selector: 'jhi-grading-instructions-details',
                field: ProgrammingExerciseInputField.ASSESSMENT_INSTRUCTIONS,
                extraCondition: () => (comp.programmingExercise.assessmentType = AssessmentType.SEMI_AUTOMATIC),
            },
            {
                name: 'presentation score',
                selector: 'jhi-presentation-score-checkbox',
                field: ProgrammingExerciseInputField.PRESENTATION_SCORE,
            },
        ];

        generateFieldVisibilityTests(testCases);
    });
});
