import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';

describe('Submission Policy Update Form Component', () => {
    const lockRepositoryPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 } as LockRepositoryPolicy;
    const submissionPenaltyPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY, submissionLimit: 5, exceedingPenalty: 50.4 } as SubmissionPenaltyPolicy;
    const brokenPenaltyPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY } as SubmissionPenaltyPolicy;

    let fixture: ComponentFixture<SubmissionPolicyUpdateComponent>;
    let component: SubmissionPolicyUpdateComponent;
    let expectedProgrammingExercise: ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [SubmissionPolicyUpdateComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockComponent(HelpIconComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(SubmissionPolicyUpdateComponent);
        component = fixture.componentInstance;

        expectedProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedProgrammingExercise.submissionPolicy = lockRepositoryPolicy;
        component.programmingExercise = expectedProgrammingExercise;
    });

    it('should set policy object on exercise', fakeAsync(() => {
        component.programmingExercise.submissionPolicy = undefined;
        component.ngOnInit();
        fixture.detectChanges();
        tick();

        expect(expectedProgrammingExercise.submissionPolicy).toBeUndefined();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        // We revert the enum values, since 'none' is the default type for the type picker. Therefore we
        // need to set the picker to anything other than 'none' to test switching back to 'none' appropriately
        for (const type of Object.values(SubmissionPolicyType).reverse()) {
            submissionPolicyTypeField.value = type;
            submissionPolicyTypeField.dispatchEvent(new Event('change'));
            fixture.detectChanges();
            tick();

            expect(expectedProgrammingExercise.submissionPolicy?.type).toBe(type);
            expect(expectedProgrammingExercise.submissionPolicy?.id).toBeUndefined();
        }
    }));

    it('should set submission limit correctly for all policy types', fakeAsync(() => {
        component.ngOnInit();
        fixture.detectChanges();
        tick();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        for (const type of [SubmissionPolicyType.LOCK_REPOSITORY, SubmissionPolicyType.SUBMISSION_PENALTY]) {
            submissionPolicyTypeField.value = type;
            submissionPolicyTypeField.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            tick();

            const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
            component.submissionLimitControl.setValue(10);
            submissionLimitInputField.dispatchEvent(new Event('input'));
            tick();

            expect(expectedProgrammingExercise.submissionPolicy?.submissionLimit).toBe(10);
        }
    }));

    it('should set exceeding penalty correctly for submission penalty type', fakeAsync(() => {
        component.ngOnInit();
        fixture.detectChanges();
        tick();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        submissionPolicyTypeField.value = SubmissionPolicyType.SUBMISSION_PENALTY;
        submissionPolicyTypeField.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        tick();

        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');
        component.exceedingPenaltyControl.setValue(73.73);
        submissionLimitExceededPenaltyInputField.dispatchEvent(new Event('input'));
        tick();

        expect(expectedProgrammingExercise.submissionPolicy?.exceedingPenalty).toBe(73.73);
    }));

    it('should display correct input fields when penalty policy (lock repo) is already set', fakeAsync(() => {
        expectedProgrammingExercise.submissionPolicy = lockRepositoryPolicy;
        component.ngOnInit();
        fixture.detectChanges();
        tick();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(submissionLimitInputField.value).toBe('5');
    }));

    it('should display correct input fields when penalty policy is already set', fakeAsync(() => {
        expectedProgrammingExercise.submissionPolicy = submissionPenaltyPolicy;
        component.ngOnInit();
        fixture.detectChanges();
        tick();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.SUBMISSION_PENALTY);
        expect(submissionLimitInputField.value).toBe('5');
        expect(submissionLimitExceededPenaltyInputField.value).toBe('50.4');
    }));

    it('should display correct input fields when set policy is broken', fakeAsync(() => {
        expectedProgrammingExercise.submissionPolicy = brokenPenaltyPolicy;
        component.ngOnInit();
        fixture.detectChanges();
        tick();

        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        const submissionLimitExceededPenaltyInputField = fixture.nativeElement.querySelector('#field_submissionLimitExceededPenalty');

        expect(submissionLimitInputField.value).toBe('');
        expect(submissionLimitExceededPenaltyInputField.value).toBe('');
    }));

    it('should not be invalid when no policy is undefined', () => {
        component.programmingExercise.submissionPolicy = undefined;
        component.ngOnInit();
        fixture.detectChanges();
        expect(component.invalid).toBeFalse();
    });

    it('should not be invalid when no policy is of type none', () => {
        component.programmingExercise.submissionPolicy = { type: SubmissionPolicyType.NONE };
        component.ngOnInit();
        fixture.detectChanges();
        expect(component.invalid).toBeFalse();
    });
});
