import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockDirective } from 'ng-mocks';
import { ActivatedRoute } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ProgrammingExerciseGradingComponent } from 'app/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseLifecycleComponent } from 'app/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

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
            imports: [MockDirective(NgbTooltip), MockDirective(NgbCollapse)],

            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
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
        comp.programmingExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
        comp.programmingExercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        comp.programmingExercise.bonusPoints = undefined;

        fixture.detectChanges();

        fixture.whenStable().then(() => {
            const result = comp.getGradingSummary();
            expect(result).not.toBe('');
        });
    }));

    it('should create a grading summary with exceeding penalty', fakeAsync(() => {
        comp.programmingExercise.submissionPolicy = {
            type: SubmissionPolicyType.SUBMISSION_PENALTY,
            exceedingPenalty: 10,
            submissionLimit: 5,
        };
        comp.programmingExercise.maxStaticCodeAnalysisPenalty = 5;

        fixture.detectChanges();

        fixture.whenStable().then(() => {
            const result = comp.getGradingSummary();
            expect(result).not.toBe('');
        });
    }));

    it('should create a grading summary with locked repositories and disabled code analysis', fakeAsync(() => {
        comp.programmingExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 };
        comp.programmingExercise.staticCodeAnalysisEnabled = false;

        fixture.detectChanges();

        fixture.whenStable().then(() => {
            const result = comp.getGradingSummary();
            expect(result).not.toBe('');
        });
    }));

    it('should not create a grading summary when there are no points', fakeAsync(() => {
        comp.programmingExercise.maxPoints = undefined;

        fixture.detectChanges();

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
        const checkFieldVisibility = (selector: string, isVisible: boolean, afterModification = false) => {
            if (afterModification) {
                fixture.changeDetectorRef.detectChanges();
            } else {
                fixture.detectChanges();
            }
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
                    checkFieldVisibility(selector, false, true);
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
