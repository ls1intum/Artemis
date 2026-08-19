import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

describe('Submission Policy Update Form Component', () => {
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

    it('should initialize the form when the exercise with its policy arrives after mount (#13447)', async () => {
        // The grading tab mounts the form before the parent has loaded the exercise.
        programmingExercise.submissionPolicy = undefined;
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.NONE);

        const loadedExercise = new ProgrammingExercise(undefined, undefined);
        loadedExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 3 } as LockRepositoryPolicy;
        fixture.componentRef.setInput('programmingExercise', loadedExercise);
        await detectChanges();

        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(submissionLimitInputField.value).toBe('3');
        // form-level validity for a lock policy: the submission limit control decides (see the invalid getter)
        expect(component.submissionLimitControl.valid).toBe(true);
        expect(component.invalid).toBe(false);
    });

    it('should keep user edits when the exercise input re-emits (#13447)', async () => {
        programmingExercise.submissionPolicy = undefined;
        await detectChanges();

        const loadedExercise = new ProgrammingExercise(undefined, undefined);
        loadedExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 3 } as LockRepositoryPolicy;
        fixture.componentRef.setInput('programmingExercise', loadedExercise);
        await detectChanges();

        component.submissionLimitControl.setValue(10);
        component.submissionLimitControl.markAsDirty();
        await detectChanges();

        const reEmittedExercise = new ProgrammingExercise(undefined, undefined);
        reEmittedExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 3 } as LockRepositoryPolicy;
        fixture.componentRef.setInput('programmingExercise', reEmittedExercise);
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(submissionLimitInputField.value).toBe('10');
        expect(component.submissionLimitControl.value).toBe(10);
        // The parent saves the re-emitted object, so the preserved edit must exist there too.
        expect(reEmittedExercise.submissionPolicy!.submissionLimit).toBe(10);
    });

    it('should mark the re-emitted policy as none when the user switched the policy off', async () => {
        programmingExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 } as LockRepositoryPolicy;
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        submissionPolicyTypeField.value = SubmissionPolicyType.NONE;
        submissionPolicyTypeField.dispatchEvent(new Event('change'));
        await detectChanges();

        const reEmittedExercise = new ProgrammingExercise(undefined, undefined);
        reEmittedExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 3 } as LockRepositoryPolicy;
        fixture.componentRef.setInput('programmingExercise', reEmittedExercise);
        await detectChanges();

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.NONE);
        expect(reEmittedExercise.submissionPolicy!.type).toBe(SubmissionPolicyType.NONE);
    });

    it('should copy a preserved penalty edit into the re-emitted exercise', async () => {
        programmingExercise.submissionPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY, submissionLimit: 5, exceedingPenalty: 50.4 } as SubmissionPenaltyPolicy;
        await detectChanges();

        component.exceedingPenaltyControl.setValue(73.5);
        component.exceedingPenaltyControl.markAsDirty();
        await detectChanges();

        const reEmittedExercise = new ProgrammingExercise(undefined, undefined);
        reEmittedExercise.submissionPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY, submissionLimit: 5, exceedingPenalty: 50.4 } as SubmissionPenaltyPolicy;
        fixture.componentRef.setInput('programmingExercise', reEmittedExercise);
        await detectChanges();

        expect(reEmittedExercise.submissionPolicy!.exceedingPenalty).toBe(73.5);
        expect(reEmittedExercise.submissionPolicy!.submissionLimit).toBe(5);
    });

    it('should carry a policy picked on a placeholder into the asynchronously fetched exercise (import)', async () => {
        // Import-from-sharing renders an empty placeholder exercise first; the fetched exercise
        // replaces it with the id cleared, so it counts as the same unsaved exercise.
        programmingExercise.submissionPolicy = undefined;
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        submissionPolicyTypeField.value = SubmissionPolicyType.LOCK_REPOSITORY;
        submissionPolicyTypeField.dispatchEvent(new Event('change'));
        await detectChanges();
        component.submissionLimitControl.setValue(10);
        component.submissionLimitControl.markAsDirty();
        await detectChanges();

        const fetchedExercise = new ProgrammingExercise(undefined, undefined);
        fetchedExercise.submissionPolicy = { type: SubmissionPolicyType.SUBMISSION_PENALTY, submissionLimit: 5, exceedingPenalty: 50.4 } as SubmissionPenaltyPolicy;
        fixture.componentRef.setInput('programmingExercise', fetchedExercise);
        await detectChanges();

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(fetchedExercise.submissionPolicy!.type).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(fetchedExercise.submissionPolicy!.submissionLimit).toBe(10);
        expect(fetchedExercise.submissionPolicy!.exceedingPenalty).toBeUndefined();
    });

    it('should report invalid without throwing when read before the exercise input is set (#13447)', async () => {
        // The grading page binds [formInvalid]="policyUpdate.invalid" BEFORE it writes the child's
        // inputs in the same change detection pass (forward template reference), so this read must
        // neither throw NG0950 nor enable the update button.
        const freshFixture = TestBed.createComponent(SubmissionPolicyUpdateComponent);
        const freshComponent = freshFixture.componentInstance;

        expect(() => freshComponent.invalid).not.toThrow();
        expect(freshComponent.invalid).toBe(true);

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 } as LockRepositoryPolicy;
        freshFixture.componentRef.setInput('programmingExercise', exercise);
        freshFixture.componentRef.setInput('editable', true);
        freshFixture.detectChanges();
        await freshFixture.whenStable();

        expect(freshComponent.invalid).toBe(false);
    });

    it('should still initialize from the exercise when the user picked a type before it arrived (#13447)', async () => {
        // The select is interactable while the exercise is still loading; a pick in that window must not
        // latch the (nonexistent) form state, or the arriving exercise could never initialize the form.
        const freshFixture = TestBed.createComponent(SubmissionPolicyUpdateComponent);
        freshFixture.componentRef.setInput('editable', true);
        freshFixture.detectChanges();
        await freshFixture.whenStable();

        const submissionPolicyTypeField = freshFixture.nativeElement.querySelector('#field_submissionPolicy');
        submissionPolicyTypeField.value = SubmissionPolicyType.LOCK_REPOSITORY;
        submissionPolicyTypeField.dispatchEvent(new Event('change'));
        freshFixture.detectChanges();
        await freshFixture.whenStable();

        const loadedExercise = new ProgrammingExercise(undefined, undefined);
        loadedExercise.submissionPolicy = submissionPenaltyPolicy;
        freshFixture.componentRef.setInput('programmingExercise', loadedExercise);
        freshFixture.detectChanges();
        await freshFixture.whenStable();
        freshFixture.detectChanges();

        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.SUBMISSION_PENALTY);
        expect(freshFixture.nativeElement.querySelector('#field_submissionLimit').value).toBe('5');
        expect(freshFixture.componentInstance.invalid).toBe(false);
    });

    it('should initialize the form exactly once when the exercise is present at mount (#13447)', async () => {
        // The exercise edit page resolves the exercise before mounting this component.
        const typeChangeSpy = vi.fn();
        component.submissionPolicyTypeChange.subscribe(typeChangeSpy);
        await detectChanges();

        const submissionPolicyTypeField = fixture.nativeElement.querySelector('#field_submissionPolicy');
        const submissionLimitInputField = fixture.nativeElement.querySelector('#field_submissionLimit');
        expect(submissionPolicyTypeField.value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(submissionLimitInputField.value).toBe('5');
        expect(typeChangeSpy).toHaveBeenCalledTimes(1);
    });

    // The grading parent reloads on an exerciseId route change and Angular reuses this component for the same
    // route configuration, so a second exercise arrives through the same input.
    describe('navigating between two exercises on the reused grading route', () => {
        const exerciseWithPolicy = (id: number, submissionLimit: number) => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = id;
            exercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit } as LockRepositoryPolicy;
            return exercise;
        };

        it('should show the second exercise policy instead of keeping the first', async () => {
            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(1, 3));
            await detectChanges();
            expect(fixture.nativeElement.querySelector('#field_submissionLimit').value).toBe('3');

            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(2, 7));
            await detectChanges();

            expect(fixture.nativeElement.querySelector('#field_submissionPolicy').value).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
            expect(fixture.nativeElement.querySelector('#field_submissionLimit').value).toBe('7');
            expect(component.submissionLimitControl.value).toBe(7);
        });

        it('should drop edits made on the first exercise when the second arrives', async () => {
            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(1, 3));
            await detectChanges();
            component.submissionLimitControl.setValue(10);
            component.submissionLimitControl.markAsDirty();
            await detectChanges();

            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(2, 7));
            await detectChanges();

            // Keeping the edit here would submit 10 against the second exercise.
            expect(component.submissionLimitControl.value).toBe(7);
            expect(component.submissionLimitControl.dirty).toBe(false);
        });

        it('should adopt the second exercise policy type, not only its values', async () => {
            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(1, 3));
            await detectChanges();

            const secondExercise = new ProgrammingExercise(undefined, undefined);
            secondExercise.id = 2;
            secondExercise.submissionPolicy = undefined;
            fixture.componentRef.setInput('programmingExercise', secondExercise);
            await detectChanges();

            expect(fixture.nativeElement.querySelector('#field_submissionPolicy').value).toBe(SubmissionPolicyType.NONE);
            expect(component.invalid).toBe(false);
        });

        it('should detect a switch after an unsaved exercise gained its id', async () => {
            // The id arriving on a latched form is ignored value-wise, but it must still update the
            // form's identity so the NEXT exercise is recognized as a switch.
            const newExercise = new ProgrammingExercise(undefined, undefined);
            newExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 3 } as LockRepositoryPolicy;
            fixture.componentRef.setInput('programmingExercise', newExercise);
            await detectChanges();

            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(1, 3));
            await detectChanges();

            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(2, 7));
            await detectChanges();

            expect(component.submissionLimitControl.value).toBe(7);
        });

        it('should re-apply the disabled state when the switched exercise changes editability', async () => {
            fixture.componentRef.setInput('editable', false);
            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(1, 3));
            await detectChanges();
            expect(component.submissionLimitControl.disabled).toBe(true);

            fixture.componentRef.setInput('editable', true);
            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(2, 7));
            await detectChanges();

            expect(component.submissionLimitControl.enabled).toBe(true);
            expect(component.submissionLimitControl.value).toBe(7);
        });

        it('should disable the existing form when editable turns off', async () => {
            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(1, 3));
            await detectChanges();
            expect(component.submissionLimitControl.enabled).toBe(true);

            fixture.componentRef.setInput('editable', false);
            await detectChanges();

            expect(component.submissionLimitControl.disabled).toBe(true);
        });

        it('should keep edits when an unsaved exercise gains an id, which is the same exercise', async () => {
            // Creation flow: the exercise has no id until it is persisted; that is not a navigation.
            const newExercise = new ProgrammingExercise(undefined, undefined);
            newExercise.submissionPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 3 } as LockRepositoryPolicy;
            fixture.componentRef.setInput('programmingExercise', newExercise);
            await detectChanges();
            component.submissionLimitControl.setValue(10);
            component.submissionLimitControl.markAsDirty();
            await detectChanges();

            fixture.componentRef.setInput('programmingExercise', exerciseWithPolicy(1, 3));
            await detectChanges();

            expect(component.submissionLimitControl.value).toBe(10);
        });
    });
});
