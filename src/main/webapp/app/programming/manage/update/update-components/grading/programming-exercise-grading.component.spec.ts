import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal } from '@angular/core';
import { MockDirective } from 'ng-mocks';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ProgrammingExerciseGradingComponent } from 'app/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseUpdateTimelineComponent } from '../../../../shared/programming-exercise-update-timeline/programming-exercise-update-timeline.component';
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

/**
 * Typed view onto the `viewChild` signals so the spec can stub them without a blanket
 * `(comp as any)` cast. The shapes mirror the component declaration.
 */
type GradingInternals = ProgrammingExerciseGradingComponent & {
    submissionPolicyUpdateComponent: Signal<SubmissionPolicyUpdateComponent | undefined>;
    lifecycleComponent: Signal<ProgrammingExerciseUpdateTimelineComponent | undefined>;
};
const internals = (c: ProgrammingExerciseGradingComponent): GradingInternals => c as GradingInternals;

describe('ProgrammingExerciseGradingComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseGradingComponent>;
    let comp: ProgrammingExerciseGradingComponent;
    let exercise: ProgrammingExercise;
    let editFieldRecord: Record<ProgrammingExerciseInputField, boolean>;

    const route = {
        queryParams: of({}),
        url: of([{ path: 'programming-exercises' }] as UrlSegment[]),
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
        });

        fixture = TestBed.createComponent(ProgrammingExerciseGradingComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        fixture.componentRef.setInput('importOptions', { recreateBuildPlans: false, updateTemplate: false, setTestCaseVisibilityToAfterDueDate: false });
        editFieldRecord = {
            includeExerciseInCourseScoreCalculation: true,
            points: true,
            bonusPoints: true,
            submissionPolicy: true,
            timeline: true,
            assessmentInstructions: true,
            presentationScore: true,
        } as Record<ProgrammingExerciseInputField, boolean>;
        fixture.componentRef.setInput('isEditFieldDisplayedRecord', editFieldRecord);

        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.maxPoints = 10;
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.assessmentType = AssessmentType.AUTOMATIC;
        exercise.submissionPolicy = { type: SubmissionPolicyType.NONE };
        exercise.staticCodeAnalysisEnabled = true;

        fixture.componentRef.setInput('programmingExercise', exercise);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should create a grading summary', () => {
        fixture.detectChanges();

        const result = comp.getGradingSummary();
        expect(result).not.toBe('');
    });

    it('should create a grading summary for a bonus exercise with semiautomatic assessment', () => {
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        exercise.bonusPoints = undefined;

        fixture.detectChanges(false);

        const result = comp.getGradingSummary();
        expect(result).not.toBe('');
    });

    it('should create a grading summary with exceeding penalty', () => {
        exercise.submissionPolicy = {
            type: SubmissionPolicyType.SUBMISSION_PENALTY,
            exceedingPenalty: 10,
            submissionLimit: 5,
        };
        exercise.maxStaticCodeAnalysisPenalty = 5;

        fixture.detectChanges();

        const result = comp.getGradingSummary();
        expect(result).not.toBe('');
    });

    it('should create a grading summary with locked repositories and disabled code analysis', () => {
        exercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 };
        exercise.staticCodeAnalysisEnabled = false;

        fixture.detectChanges();

        const result = comp.getGradingSummary();
        expect(result).not.toBe('');
    });

    it('should not create a grading summary when there are no points', () => {
        exercise.maxPoints = undefined;

        fixture.detectChanges();

        const result = comp.getGradingSummary();
        expect(result).toBe('');
    });

    it('should return replacement for grading summary key', () => {
        fixture.detectChanges();

        const replacements = {
            exerciseType: 'replacedType',
        };

        const replacedString = comp.replacePlaceholder('"exerciseType"', 'exerciseType', replacements);

        expect(replacedString).toBe('replacedType');
    });

    it('should not return replacement for unknown grading summary key', () => {
        fixture.detectChanges();

        const replacements = {
            exerciseType: 'replacedType',
        };

        const replacedString = comp.replacePlaceholder('"exerciseType2"', 'exerciseType2', replacements);

        expect(replacedString).toBe('"exerciseType2"');
    });

    it('should update form section calculation', () => {
        const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

        const submissionPolicyUpdateComponent = { form: { valueChanges: new Subject() } } as unknown as SubmissionPolicyUpdateComponent;
        const lifecycleComponent = { formValidChanges: new Subject() } as unknown as ProgrammingExerciseUpdateTimelineComponent;
        vi.spyOn(internals(comp), 'submissionPolicyUpdateComponent').mockReturnValue(submissionPolicyUpdateComponent);
        vi.spyOn(internals(comp), 'lifecycleComponent').mockReturnValue(lifecycleComponent);

        comp.ngAfterViewInit();

        (submissionPolicyUpdateComponent.form.valueChanges as Subject<boolean>).next(false);
        lifecycleComponent.formValidChanges.next(false);

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
                fixture.detectChanges(false);
            } else {
                fixture.detectChanges(false);
            }
            if (selector === 'jhi-grading-instructions-details' && isVisible) {
                // setInput with a fresh exercise reference so the signal-driven template re-evaluates under zoneless.
                exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
                fixture.componentRef.setInput('programmingExercise', { ...exercise } as ProgrammingExercise);
                fixture.detectChanges(false);
                const instructionsField = fixture.debugElement.nativeElement.querySelector(selector);
                expect(instructionsField).not.toBeNull();
                return;
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
                    extraCondition?.();
                    fixture.detectChanges(false);
                    checkFieldVisibility(selector, true);
                });

                it('should NOT be displayed', () => {
                    extraCondition?.();
                    fixture.detectChanges(false);
                    // setInput with a fresh record reference so the signal-driven @if re-evaluates under zoneless.
                    fixture.componentRef.setInput('isEditFieldDisplayedRecord', { ...editFieldRecord, [field]: false });
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
                selector: 'jhi-programming-exercise-update-timeline',
                field: ProgrammingExerciseInputField.TIMELINE,
            },
            {
                name: 'assessment instructions',
                selector: 'jhi-grading-instructions-details',
                field: ProgrammingExerciseInputField.ASSESSMENT_INSTRUCTIONS,
                extraCondition: () => {
                    exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
                    fixture.componentRef.setInput('programmingExercise', { ...exercise } as ProgrammingExercise);
                    editFieldRecord[ProgrammingExerciseInputField.ASSESSMENT_INSTRUCTIONS] = true;
                    fixture.componentRef.setInput('isEditFieldDisplayedRecord', { ...editFieldRecord });
                },
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
