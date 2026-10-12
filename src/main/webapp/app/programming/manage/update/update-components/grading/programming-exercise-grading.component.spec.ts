import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ChangeDetectorRef, Signal, signal } from '@angular/core';
import { MockDirective } from 'ng-mocks';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { Subject, of } from 'rxjs';
import { NgModel } from '@angular/forms';
import { ProgrammingExerciseGradingComponent } from 'app/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
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
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { ExerciseGroupDateNoticeComponent } from 'app/exercise/exercise-group-date-notice/exercise-group-date-notice.component';
import { MAX_PENALTY_PATTERN } from 'app/foundation/constants/input.constants';

/**
 * Typed view onto the `viewChild` signals so the spec can stub them without a blanket
 * `(comp as any)` cast. The shapes mirror the component declaration.
 */
type GradingInternals = ProgrammingExerciseGradingComponent & {
    submissionPolicyUpdateComponent: Signal<SubmissionPolicyUpdateComponent | undefined>;
    maxScoreField: Signal<NgModel | undefined>;
};
const internals = (c: ProgrammingExerciseGradingComponent): GradingInternals => c as GradingInternals;

describe('ProgrammingExerciseGradingComponent', () => {
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
                { provide: BuildPhasesTemplateService, useValue: { getTemplate: vi.fn() } },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        fixture = TestBed.createComponent(ProgrammingExerciseGradingComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
        fixture.componentRef.setInput('importOptions', { recreateBuildPlans: false, setTestCaseVisibilityToAfterDueDate: false });
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

    it('should render the group date notice first in the controls next to the timeline', () => {
        fixture.componentRef.setInput('exercisePartOfExerciseGroup', true);
        const editGroupDatesSpy = vi.spyOn(comp.editGroupDates, 'emit');
        fixture.detectChanges();

        const timelineControls = fixture.debugElement.query(By.css('jhi-programming-exercise-timeline [data-testid="assessment-layout"]'));
        const notice = timelineControls.query(By.directive(ExerciseGroupDateNoticeComponent));

        expect(timelineControls.nativeElement.firstElementChild).toBe(notice.nativeElement);

        (notice.componentInstance as ExerciseGroupDateNoticeComponent).editGroupDates.emit();

        expect(editGroupDatesSpy).toHaveBeenCalledOnce();
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
        const policyFormChanges = new Subject<boolean>();
        const submissionPolicyUpdateComponent = { policyForm: signal({ valueChanges: policyFormChanges }) } as unknown as SubmissionPolicyUpdateComponent;
        vi.spyOn(internals(comp), 'submissionPolicyUpdateComponent').mockReturnValue(submissionPolicyUpdateComponent);

        fixture.detectChanges();
        const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

        policyFormChanges.next(false);

        expect(calculateFormStatusSpy).toHaveBeenCalledOnce();

        comp.onTimelineStatusChange({ valid: false, empty: true, invalidItems: [] });

        expect(calculateFormStatusSpy).toHaveBeenCalledTimes(2);
        expect(comp.timelineStatus()).toEqual({ valid: false, empty: true, invalidItems: [] });
    });

    it('should follow the form of the submission policy component that is only built after the component itself exists', () => {
        const policyForm = signal<{ valueChanges: Subject<boolean> } | undefined>(undefined);
        const submissionPolicyUpdateComponent = { policyForm } as unknown as SubmissionPolicyUpdateComponent;
        vi.spyOn(internals(comp), 'submissionPolicyUpdateComponent').mockReturnValue(submissionPolicyUpdateComponent);
        fixture.detectChanges();
        const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');
        const policyFormChanges = new Subject<boolean>();

        expect(calculateFormStatusSpy).not.toHaveBeenCalled();

        policyForm.set({ valueChanges: policyFormChanges });
        fixture.detectChanges();
        expect(policyFormChanges.observed).toBe(true);
        calculateFormStatusSpy.mockClear();

        policyFormChanges.next(false);

        expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
    });

    describe('subscriptions that follow the fields', () => {
        // the viewChild of the submission policy component is stubbed with a signal, so it can be replaced like the real one while the page changes
        const stubSubmissionPolicyComponent = (initialForm: { valueChanges: Subject<unknown> } | undefined) => {
            const policyForm = signal(initialForm);
            internals(comp).submissionPolicyUpdateComponent = signal({ policyForm } as unknown as SubmissionPolicyUpdateComponent);
            return policyForm;
        };

        it('should not recalculate the form status before the fields of the section exist', async () => {
            const original = comp.calculateFormStatus.bind(comp);
            const calculationsWithoutFields: string[] = [];
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus').mockImplementation(() => {
                if (!comp.maxScoreField() || !comp.bonusPointsField()) {
                    calculationsWithoutFields.push('max points or bonus points field missing');
                }
                original();
            });

            fixture.detectChanges();
            await fixture.whenStable();

            // a calculation before the fields exist would find them invalid and report a form that is fine as invalid
            expect(calculateFormStatusSpy).toHaveBeenCalled();
            expect(calculationsWithoutFields).toEqual([]);
            expect(comp.formValid).toBe(true);
        });

        it('should stop following the form of the submission policy when the section is destroyed', () => {
            const policyFormChanges = new Subject<unknown>();
            stubSubmissionPolicyComponent({ valueChanges: policyFormChanges });
            fixture.detectChanges();
            expect(policyFormChanges.observed).toBe(true);
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            policyFormChanges.next(null);
            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
            fixture.destroy();
            policyFormChanges.next(null);

            expect(policyFormChanges.observed).toBe(false);
            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
        });

        it('should follow a replaced form of the submission policy and let go of the previous one', () => {
            const firstFormChanges = new Subject<unknown>();
            const secondFormChanges = new Subject<unknown>();
            const policyForm = stubSubmissionPolicyComponent({ valueChanges: firstFormChanges });
            fixture.detectChanges();
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            policyForm.set({ valueChanges: secondFormChanges });
            fixture.detectChanges();
            calculateFormStatusSpy.mockClear();

            expect(firstFormChanges.observed).toBe(false);
            expect(secondFormChanges.observed).toBe(true);
            firstFormChanges.next(null);
            expect(calculateFormStatusSpy).not.toHaveBeenCalled();
            secondFormChanges.next(null);
            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
        });

        it('should let go of the form of the submission policy when the form is gone', () => {
            const policyFormChanges = new Subject<unknown>();
            const policyForm = stubSubmissionPolicyComponent({ valueChanges: policyFormChanges });
            fixture.detectChanges();
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            policyForm.set(undefined);
            fixture.detectChanges();
            calculateFormStatusSpy.mockClear();
            policyFormChanges.next(null);

            expect(policyFormChanges.observed).toBe(false);
            expect(calculateFormStatusSpy).not.toHaveBeenCalled();
        });

        it('should follow a submission policy component that appears after the first render and let go of it when it disappears', () => {
            const policyFormChanges = new Subject<unknown>();
            const policyComponent = signal<SubmissionPolicyUpdateComponent | undefined>(undefined);
            internals(comp).submissionPolicyUpdateComponent = policyComponent;
            fixture.detectChanges();
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            policyComponent.set({ policyForm: signal({ valueChanges: policyFormChanges }) } as unknown as SubmissionPolicyUpdateComponent);
            fixture.detectChanges();
            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
            policyFormChanges.next(null);
            expect(calculateFormStatusSpy).toHaveBeenCalledTimes(2);

            policyComponent.set(undefined);
            fixture.detectChanges();
            expect(calculateFormStatusSpy).toHaveBeenCalledTimes(3);
            expect(policyFormChanges.observed).toBe(false);
            policyFormChanges.next(null);
            expect(calculateFormStatusSpy).toHaveBeenCalledTimes(3);
        });
    });

    describe('fields that appear after the first render', () => {
        const maxPenaltyInput = () => fixture.debugElement.nativeElement.querySelector('#field_maxPenalty') as HTMLInputElement | null;

        const type = async (input: HTMLInputElement, value: string) => {
            input.value = value;
            input.dispatchEvent(new Event('input'));
            await fixture.whenStable();
        };

        // The max penalty only exists once static code analysis is on, which is switched on after the section has rendered.
        it('should recalculate the form status when the max penalty is edited after static code analysis was switched on', async () => {
            fixture.componentRef.setInput(
                'programmingExerciseCreationConfig',
                Object.assign({}, programmingExerciseCreationConfigMock, { maxPenaltyPattern: MAX_PENALTY_PATTERN }),
            );
            exercise.staticCodeAnalysisEnabled = false;
            fixture.detectChanges();
            expect(maxPenaltyInput()).toBeNull();

            fixture.componentRef.setInput('programmingExercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise, { staticCodeAnalysisEnabled: true }));
            fixture.detectChanges();
            await fixture.whenStable();
            expect(maxPenaltyInput()).not.toBeNull();
            const formValidBeforeEditing = comp.formValid;

            await type(maxPenaltyInput()!, '150');
            expect(comp.formValid).toBe(false);

            await type(maxPenaltyInput()!, '50');
            expect(comp.formValid).toBe(formValidBeforeEditing);
        });

        // An invalid max penalty stops counting once static code analysis is switched off, because its field is gone with it.
        it('should recalculate the form status when the max penalty disappears together with static code analysis', async () => {
            fixture.componentRef.setInput(
                'programmingExerciseCreationConfig',
                Object.assign({}, programmingExerciseCreationConfigMock, { maxPenaltyPattern: MAX_PENALTY_PATTERN }),
            );
            // the form edits one exercise object and the other parts of the page change it in place, so the input itself does not change
            const editedExercise = Object.assign(new ProgrammingExercise(undefined, undefined), exercise, { staticCodeAnalysisEnabled: true });
            fixture.componentRef.setInput('programmingExercise', editedExercise);
            fixture.detectChanges();
            await fixture.whenStable();
            const formValidBeforeEditing = comp.formValid;
            await type(maxPenaltyInput()!, '150');
            expect(comp.formValid).toBe(false);

            editedExercise.staticCodeAnalysisEnabled = false;
            fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
            fixture.detectChanges();
            await fixture.whenStable();

            expect(maxPenaltyInput()).toBeNull();
            expect(comp.formValid).toBe(formValidBeforeEditing);
        });

        const valueOf = (selector: string) => (fixture.debugElement.nativeElement.querySelector(selector) as HTMLInputElement | null)?.value;

        // A hidden field cannot be valid, so the form is invalid while the points are missing from the page and valid once they appear.
        it('should recalculate the form status when the max points appear and edit them afterwards', async () => {
            editFieldRecord.points = false;
            fixture.componentRef.setInput('isEditFieldDisplayedRecord', { ...editFieldRecord });
            fixture.detectChanges();
            await fixture.whenStable();
            expect(valueOf('#field_points')).toBeUndefined();
            expect(comp.formValid).toBe(false);

            fixture.componentRef.setInput('isEditFieldDisplayedRecord', { ...editFieldRecord, points: true });
            fixture.detectChanges();
            await fixture.whenStable();
            expect(valueOf('#field_points')).toBe('10');
            expect(comp.formValid).toBe(true);
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            await type(fixture.debugElement.nativeElement.querySelector('#field_points'), '0');
            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
            expect(comp.formValid).toBe(false);

            await type(fixture.debugElement.nativeElement.querySelector('#field_points'), '5');
            expect(calculateFormStatusSpy).toHaveBeenCalledTimes(2);
            expect(comp.formValid).toBe(true);
        });

        it('should recalculate the form status when the bonus points appear and edit them afterwards', async () => {
            exercise.bonusPoints = 2;
            editFieldRecord.bonusPoints = false;
            fixture.componentRef.setInput('isEditFieldDisplayedRecord', { ...editFieldRecord });
            fixture.detectChanges();
            await fixture.whenStable();
            expect(valueOf('#field_bonusPoints')).toBeUndefined();
            expect(comp.formValid).toBe(false);

            fixture.componentRef.setInput('isEditFieldDisplayedRecord', { ...editFieldRecord, bonusPoints: true });
            fixture.detectChanges();
            await fixture.whenStable();
            expect(valueOf('#field_bonusPoints')).toBe('2');
            expect(comp.formValid).toBe(true);
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            await type(fixture.debugElement.nativeElement.querySelector('#field_bonusPoints'), '-1');
            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
            expect(comp.formValid).toBe(false);

            await type(fixture.debugElement.nativeElement.querySelector('#field_bonusPoints'), '3');
            expect(calculateFormStatusSpy).toHaveBeenCalledTimes(2);
            expect(comp.formValid).toBe(true);
        });

        it('should recalculate the form status when the max points or the bonus points of the first render are edited', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            await type(fixture.debugElement.nativeElement.querySelector('#field_points'), '10000');
            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
            expect(comp.formValid).toBe(false);
            await type(fixture.debugElement.nativeElement.querySelector('#field_points'), '10');
            expect(comp.formValid).toBe(true);

            await type(fixture.debugElement.nativeElement.querySelector('#field_bonusPoints'), '10000');
            expect(comp.formValid).toBe(false);
            await type(fixture.debugElement.nativeElement.querySelector('#field_bonusPoints'), '0');
            expect(comp.formValid).toBe(true);
        });

        // The submission policy component builds its form in an effect of its own, so the form does not exist yet while the viewChild
        // of this component already does. This uses the real child, a stubbed one would already come with its form.
        it('should recalculate the form status when the form of the real submission policy component changes', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            const submissionPolicyForm = comp.submissionPolicyUpdateComponent()!.form;
            expect(submissionPolicyForm).toBeDefined();
            const calculateFormStatusSpy = vi.spyOn(comp, 'calculateFormStatus');

            submissionPolicyForm.get('submissionLimit')!.setValue(3);

            expect(calculateFormStatusSpy).toHaveBeenCalledOnce();
        });

        it('should become invalid when the real submission policy is a lock without a limit and valid again once the limit is entered', async () => {
            exercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 };
            fixture.detectChanges();
            await fixture.whenStable();
            expect(comp.formValid).toBe(true);
            const submissionLimitControl = comp.submissionPolicyUpdateComponent()!.form.get('submissionLimit')!;

            submissionLimitControl.setValue(null);
            expect(comp.submissionPolicyUpdateComponent()!.invalid).toBe(true);
            expect(comp.formValid).toBe(false);

            submissionLimitControl.setValue(3);
            expect(comp.submissionPolicyUpdateComponent()!.invalid).toBe(false);
            expect(comp.formValid).toBe(true);
        });
    });

    it('should not require points when exercise is not included in the course score', () => {
        exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
        exercise.maxPoints = undefined;
        exercise.staticCodeAnalysisEnabled = false;
        fixture.componentRef.setInput('programmingExercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise));
        fixture.detectChanges(false);

        const pointsInput = fixture.debugElement.nativeElement.querySelector('#field_points') as HTMLInputElement;
        expect(pointsInput.required).toBe(false);
        expect(pointsInput.min).toBe('0');
        const pointsFormGroup = pointsInput.closest('.form-group') as HTMLElement | null;
        expect(pointsFormGroup).not.toBeNull();
        expect(pointsFormGroup?.hidden).toBe(false);

        vi.spyOn(internals(comp), 'maxScoreField').mockReturnValue({ valid: false } as NgModel);
        vi.spyOn(internals(comp), 'submissionPolicyUpdateComponent').mockReturnValue({ invalid: false } as SubmissionPolicyUpdateComponent);
        comp.timelineStatus.set({ valid: true, empty: false, invalidItems: [] });

        comp.calculateFormStatus();

        expect(comp.formValid).toBe(true);
    });

    it('should keep invalid points invalid when exercise is not included in the course score', () => {
        exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
        exercise.maxPoints = -1;
        exercise.staticCodeAnalysisEnabled = false;
        fixture.componentRef.setInput('programmingExercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise));
        fixture.detectChanges(false);

        vi.spyOn(internals(comp), 'maxScoreField').mockReturnValue({ valid: false } as NgModel);
        vi.spyOn(internals(comp), 'submissionPolicyUpdateComponent').mockReturnValue({ invalid: false } as SubmissionPolicyUpdateComponent);
        comp.timelineStatus.set({ valid: true, empty: false, invalidItems: [] });

        comp.calculateFormStatus();

        expect(comp.formValid).toBe(false);
    });

    it('should set points to zero when exercise is not included in the course score', () => {
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.maxPoints = 1;
        exercise.bonusPoints = 5;
        const programmingExercise = Object.assign(new ProgrammingExercise(undefined, undefined), exercise);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges(false);

        comp.onIncludedInOverallScoreChange(IncludedInOverallScore.NOT_INCLUDED);

        expect(programmingExercise.includedInOverallScore).toBe(IncludedInOverallScore.NOT_INCLUDED);
        expect(programmingExercise.maxPoints).toBe(0);
        expect(programmingExercise.bonusPoints).toBe(0);
    });

    it('should set bonus points to zero when exercise is included as bonus', () => {
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.maxPoints = 1;
        exercise.bonusPoints = 5;
        const programmingExercise = Object.assign(new ProgrammingExercise(undefined, undefined), exercise);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges(false);

        comp.onIncludedInOverallScoreChange(IncludedInOverallScore.INCLUDED_AS_BONUS);

        expect(programmingExercise.includedInOverallScore).toBe(IncludedInOverallScore.INCLUDED_AS_BONUS);
        expect(programmingExercise.bonusPoints).toBe(0);
    });

    it('should restore the minimum valid points when exercise is included again', () => {
        exercise.includedInOverallScore = IncludedInOverallScore.NOT_INCLUDED;
        exercise.maxPoints = 0;
        const programmingExercise = Object.assign(new ProgrammingExercise(undefined, undefined), exercise);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges(false);

        comp.onIncludedInOverallScoreChange(IncludedInOverallScore.INCLUDED_COMPLETELY);

        expect(programmingExercise.includedInOverallScore).toBe(IncludedInOverallScore.INCLUDED_COMPLETELY);
        expect(programmingExercise.maxPoints).toBe(1);
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
                fixture.componentRef.setInput('programmingExercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise));
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
                selector: 'jhi-programming-exercise-timeline',
                field: ProgrammingExerciseInputField.TIMELINE,
            },
            {
                name: 'assessment instructions',
                selector: 'jhi-grading-instructions-details',
                field: ProgrammingExerciseInputField.ASSESSMENT_INSTRUCTIONS,
                extraCondition: () => {
                    exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
                    fixture.componentRef.setInput('programmingExercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise));
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
