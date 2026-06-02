import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

describe('Submission Policy Update Form Component', () => {
    setupTestBed({ zoneless: true });

    const lockRepositoryPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 } as LockRepositoryPolicy;
    const submissionPenaltyPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY, submissionLimit: 5, exceedingPenalty: 50.4 } as SubmissionPenaltyPolicy;
    const brokenPenaltyPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY } as SubmissionPenaltyPolicy;

    let fixture: ComponentFixture<SubmissionPolicyUpdateComponent>;
    let component: SubmissionPolicyUpdateComponent;
    let programmingExercise: ProgrammingExercise;

    const mockTranslateDirective = MockDirective(TranslateDirective);
    const mockHelpIconComponent = MockComponent(HelpIconComponent);

    const detectChanges = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SubmissionPolicyUpdateComponent, MockPipe(ArtemisTranslatePipe), mockTranslateDirective, mockHelpIconComponent],
        })
            .overrideComponent(SubmissionPolicyUpdateComponent, {
                remove: { imports: [TranslateDirective, HelpIconComponent] },
                add: { imports: [mockTranslateDirective, mockHelpIconComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(SubmissionPolicyUpdateComponent);
        component = fixture.componentInstance;

        programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.submissionPolicy = lockRepositoryPolicy;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.componentRef.setInput('editable', true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set policy object on exercise', async () => {
        programmingExercise.submissionPolicy = undefined;
        await detectChanges();

        expect(programmingExercise.submissionPolicy).toBeUndefined();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        // We revert the enum values, since 'none' is the default type for the type picker. Therefore we
        // need to set the picker to anything other than 'none' to test switching back to 'none' appropriately
        for (const type of Object.values(SubmissionPolicyType).reverse()) {
            submissionPolicyTypeField.value = type;
            submissionPolicyTypeField.dispatchEvent(new Event('change'));
            await detectChanges();

            expect(programmingExercise.submissionPolicy!.type).toBe(type);
            expect(programmingExercise.submissionPolicy!.id).toBeUndefined();
        }
    });

    it('should set submission limit correctly for all policy types', async () => {
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        for (const type of [SubmissionPolicyType.LOCK_REPOSITORY, SubmissionPolicyType.SUBMISSION_PENALTY]) {
            submissionPolicyTypeField.value = type;
            submissionPolicyTypeField.dispatchEvent(new Event('change'));
            await detectChanges();

            const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
            component.submissionLimitControl.setValue(10);
            submissionLimitInputField.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            expect(programmingExercise.submissionPolicy?.submissionLimit).toBe(10);
        }
    });

    it('should set exceeding penalty correctly for submission penalty type', async () => {
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        submissionPolicyTypeField.value = SubmissionPolicyType.SUBMISSION_PENALTY;
        submissionPolicyTypeField.dispatchEvent(new Event('change'));
        await detectChanges();

        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');
        component.exceedingPenaltyControl.setValue(73.73);
        submissionLimitExceededPenaltyInputField.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(programmingExercise.submissionPolicy?.exceedingPenalty).toBe(73.73);
    });

    it('should display correct input fields when penalty policy (lock repo) is already set', async () => {
        programmingExercise.submissionPolicy = lockRepositoryPolicy;
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(submissionLimitInputField.value).toBe('5');
    });

    it('should display correct input fields when penalty policy is already set', async () => {
        programmingExercise.submissionPolicy = submissionPenaltyPolicy;
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.SUBMISSION_PENALTY);
        expect(submissionLimitInputField.value).toBe('5');
        expect(submissionLimitExceededPenaltyInputField.value).toBe('50.4');
    });

    it('should display correct input fields when set policy is broken', async () => {
        programmingExercise.submissionPolicy = brokenPenaltyPolicy;
        await detectChanges();

        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');

        expect(submissionLimitInputField.value).toBe('');
        expect(submissionLimitExceededPenaltyInputField.value).toBe('');
    });

    it('should not be invalid when no policy is undefined', async () => {
        programmingExercise.submissionPolicy = undefined;
        await detectChanges();
        expect(component.invalid).toBe(false);
    });

    it('should not be invalid when no policy is of type none', async () => {
        programmingExercise.submissionPolicy = { type: SubmissionPolicyType.NONE };
        await detectChanges();
        expect(component.invalid).toBe(false);
    });
});
