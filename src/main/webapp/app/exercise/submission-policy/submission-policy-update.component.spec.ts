import { expect } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Submission Policy Update Form Component', () => {
    setupTestBed({ zoneless: true });
    const lockRepositoryPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 } as LockRepositoryPolicy;
    const submissionPenaltyPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY, submissionLimit: 5, exceedingPenalty: 50.4 } as SubmissionPenaltyPolicy;
    const brokenPenaltyPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY } as SubmissionPenaltyPolicy;

    let fixture: ComponentFixture<SubmissionPolicyUpdateComponent>;
    let component: SubmissionPolicyUpdateComponent;
    let expectedProgrammingExercise: ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockComponent(HelpIconComponent), FormsModule, ReactiveFormsModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(SubmissionPolicyUpdateComponent);
        component = fixture.componentInstance;

        expectedProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedProgrammingExercise.submissionPolicy = lockRepositoryPolicy;
        component.programmingExercise = expectedProgrammingExercise;
    });

    it('should set policy object on exercise', () => {
        component.programmingExercise.submissionPolicy = undefined;
        component.ngOnInit();
        fixture.detectChanges();
        expect(expectedProgrammingExercise.submissionPolicy).toBeUndefined();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        // We revert the enum values, since 'none' is the default type for the type picker. Therefore we
        // need to set the picker to anything other than 'none' to test switching back to 'none' appropriately
        for (const type of Object.values(SubmissionPolicyType).reverse()) {
            submissionPolicyTypeField.value = type;
            submissionPolicyTypeField.dispatchEvent(new Event('change'));
            fixture.detectChanges();

            expect(expectedProgrammingExercise.submissionPolicy?.type).toBe(type);
            expect(expectedProgrammingExercise.submissionPolicy?.id).toBeUndefined();
        }
    });

    it('should set submission limit correctly for all policy types', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        for (const type of [SubmissionPolicyType.LOCK_REPOSITORY, SubmissionPolicyType.SUBMISSION_PENALTY]) {
            submissionPolicyTypeField.value = type;
            submissionPolicyTypeField.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
            component.submissionLimitControl.setValue(10);
            submissionLimitInputField.dispatchEvent(new Event('input'));

            expect(expectedProgrammingExercise.submissionPolicy?.submissionLimit).toBe(10);
        }
    });

    it('should set exceeding penalty correctly for submission penalty type', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        submissionPolicyTypeField.value = SubmissionPolicyType.SUBMISSION_PENALTY;
        submissionPolicyTypeField.dispatchEvent(new Event('change'));
        fixture.detectChanges();

        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');
        component.exceedingPenaltyControl.setValue(73.73);
        submissionLimitExceededPenaltyInputField.dispatchEvent(new Event('input'));

        expect(expectedProgrammingExercise.submissionPolicy?.exceedingPenalty).toBe(73.73);
    });

    it('should display correct input fields when penalty policy (lock repo) is already set', async () => {
        expectedProgrammingExercise.submissionPolicy = lockRepositoryPolicy;
        component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();
        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(submissionLimitInputField.value).toBe('5');
    });

    it('should display correct input fields when penalty policy is already set', async () => {
        expectedProgrammingExercise.submissionPolicy = submissionPenaltyPolicy;
        component.ngOnInit();
        fixture.detectChanges();
        await fixture.whenStable();
        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.SUBMISSION_PENALTY);
        expect(submissionLimitInputField.value).toBe('5');
        expect(submissionLimitExceededPenaltyInputField.value).toBe('50.4');
    });

    it('should display correct input fields when set policy is broken', () => {
        expectedProgrammingExercise.submissionPolicy = brokenPenaltyPolicy;
        component.ngOnInit();
        fixture.detectChanges();
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');

        expect(submissionLimitInputField.value).toBe('');
        expect(submissionLimitExceededPenaltyInputField.value).toBe('');
    });

    it('should not be invalid when no policy is undefined', () => {
        component.programmingExercise.submissionPolicy = undefined;
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(component.invalid).toBe(false);
    });

    it('should not be invalid when no policy is of type none', () => {
        component.programmingExercise.submissionPolicy = { type: SubmissionPolicyType.NONE };
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(component.invalid).toBe(false);
    });
});
