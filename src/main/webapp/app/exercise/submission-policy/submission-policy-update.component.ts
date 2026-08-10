import { Component, effect, input, output, signal, untracked } from '@angular/core';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { KeyValuePipe } from '@angular/common';

@Component({
    selector: 'jhi-submission-policy-update',
    template: `
        <div class="form-group-narrow mb-3">
            <label class="label-narrow" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.title" for="field_submissionPolicy">Submission Policy</label>
            <select
                #policy="ngModel"
                required
                class="form-select"
                [ngModel]="selectedSubmissionPolicyType()"
                (ngModelChange)="onSubmissionPolicyTypeChanged($event)"
                name="submissionPolicyType"
                id="field_submissionPolicy"
                [disabled]="!editable()"
            >
                <option value="none" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.none.optionLabel"></option>
                <option value="lock_repository" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.lockRepository.optionLabel"></option>
                <option value="submission_penalty" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionPenalty.optionLabel"></option>
            </select>
        </div>
        @if (!isNonePolicy()) {
            <form [formGroup]="form">
                <div class="row mb-3">
                    <div class="col">
                        <ng-container>
                            <label
                                class="label-narrow"
                                jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle"
                                for="field_submissionLimitExceededPenalty"
                                >Submission limit</label
                            >
                            <jhi-help-icon text="artemisApp.programmingExercise.submissionPolicy.submissionLimitDescription" />
                            <div class="input-group">
                                <input
                                    required
                                    type="number"
                                    formControlName="submissionLimit"
                                    class="form-control"
                                    step="1"
                                    name="submissionLimit"
                                    id="field_submissionLimit"
                                    (input)="updateSubmissionLimit()"
                                />
                            </div>
                            @for (e of submissionLimitControl.errors! | keyvalue; track e) {
                                @if (submissionLimitControl.invalid && (submissionLimitControl.dirty || submissionLimitControl.touched)) {
                                    <div class="alert alert-danger">
                                        <div [jhiTranslate]="'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning' + '.' + e.key"></div>
                                    </div>
                                }
                            }
                        </ng-container>
                    </div>
                    <div class="col">
                        @if (this.isSubmissionPenaltyPolicy) {
                            <label
                                class="label-narrow"
                                jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldTitle"
                                for="field_submissionLimitExceededPenalty"
                                >Penalty after Exceeding Submission limit</label
                            >
                            <jhi-help-icon text="artemisApp.programmingExercise.submissionPolicy.submissionPenalty.exceedingLimitDescription" />
                            <div class="input-group">
                                <input
                                    required
                                    type="number"
                                    class="form-control"
                                    formControlName="exceedingPenalty"
                                    name="submissionLimitExceededPenalty"
                                    id="field_submissionLimitExceededPenalty"
                                    (input)="updateExceedingPenalty()"
                                />
                            </div>
                            @for (e of exceedingPenaltyControl.errors! | keyvalue; track e) {
                                @if (exceedingPenaltyControl.invalid && (exceedingPenaltyControl.dirty || exceedingPenaltyControl.touched)) {
                                    <div class="alert alert-danger">
                                        <div
                                            [jhiTranslate]="'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning' + '.' + e.key"
                                        ></div>
                                    </div>
                                }
                            }
                        }
                    </div>
                </div>
            </form>
        }
    `,
    styleUrls: ['../../programming/shared/programming-exercise-form.scss'],
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, HelpIconComponent, KeyValuePipe],
})
export class SubmissionPolicyUpdateComponent {
    // Not input.required: the grading page binds [formInvalid]="policyUpdate.invalid" before this
    // component's inputs are written in the same change detection pass (forward template reference),
    // so a required input would throw NG0950 and abort the whole view (#13447).
    readonly programmingExercise = input<ProgrammingExercise>();
    readonly editable = input(false);

    readonly submissionPolicyTypeChange = output<void>();

    form!: FormGroup; // built on the first programmingExercise emission, before the reactive form template renders

    // Both start as "none" so the reactive form part of the template stays hidden until the
    // exercise input arrives and the effect below initializes the form.
    readonly selectedSubmissionPolicyType = signal<SubmissionPolicyType>(SubmissionPolicyType.NONE);

    isSubmissionPenaltyPolicy = false;
    isLockRepositoryPolicy = false;
    readonly isNonePolicy = signal(true);

    // This is used to ensure that only integers [1-500] can be used as input for the submission limit.
    submissionLimitPattern = '^([1-9]|([1-9][0-9])|([1-4][0-9][0-9])|500)$';

    submissionLimitControl!: FormControl; // resolved from the form right after its creation, before the reactive form template renders
    exceedingPenaltyControl!: FormControl; // resolved from the form right after its creation, before the reactive form template renders

    // penalty can be any (point) number greater than 0
    exceedingPenaltyPattern = RegExp('^0*[1-9][0-9]*(\\.[0-9]+)?|0+\\.[0-9]*[1-9][0-9]*$');

    // Set once the form received a persisted policy or the user changed the policy type. From then on,
    // later programmingExercise emissions for the SAME exercise must not overwrite the form.
    private policyFormInitialized = false;

    // Id of the exercise the form currently shows. The grading route is reused when navigating from one
    // exercise's submission policy tab to another, so without this the latch above would reject the new
    // exercise and leave the previous one's policy on screen while the actions target the new one.
    private initializedExerciseId?: number;

    constructor() {
        // The exercise can arrive after this component mounted (the grading page shows the submission policy
        // tab while the exercise is still loading, #13447), so the form (re-)initializes on every emission
        // until it received a real policy or the user started editing.
        effect(() => {
            const programmingExercise = this.programmingExercise();
            if (programmingExercise) {
                untracked(() => this.initializeFormFromExercise(programmingExercise));
            }
        });
        // The editable input can change while the form exists (the reused grading route derives it from
        // each exercise's permissions); control disabled state does not follow the input on its own.
        effect(() => {
            const editable = this.editable();
            untracked(() => {
                if (!this.form) {
                    return;
                }
                for (const control of [this.submissionLimitControl, this.exceedingPenaltyControl]) {
                    if (editable) {
                        control.enable({ emitEvent: false });
                    } else {
                        control.disable({ emitEvent: false });
                    }
                }
            });
        });
    }

    /**
     * Whether this emission belongs to a different exercise than the one on screen.
     *
     * Only a switch between two known ids counts. An id appearing on a previously unsaved exercise is the
     * same entity being persisted, not a new one, so the user's input survives it.
     */
    private hasSwitchedExercise(programmingExercise: ProgrammingExercise): boolean {
        const exerciseId = programmingExercise.id;
        return exerciseId !== undefined && this.initializedExerciseId !== undefined && exerciseId !== this.initializedExerciseId;
    }

    private initializeFormFromExercise(programmingExercise: ProgrammingExercise): void {
        const switchedExercise = this.hasSwitchedExercise(programmingExercise);
        // Record the id even when the emission is otherwise ignored below: an unsaved exercise that gains
        // its id while the latch is set must still update the form's identity, or a later switch to a
        // different exercise would go undetected and keep this exercise's values on screen.
        this.initializedExerciseId = programmingExercise.id ?? this.initializedExerciseId;
        if (!switchedExercise && (this.policyFormInitialized || this.form?.dirty || this.form?.touched)) {
            return;
        }
        if (switchedExercise) {
            // Nothing from the previous exercise may survive: its values are reset below, and the latch has
            // to drop as well or the new exercise's persisted policy would be rejected right after.
            this.policyFormInitialized = false;
        }
        const submissionPolicy = programmingExercise.submissionPolicy;
        this.applySubmissionPolicyType(submissionPolicy?.type ?? SubmissionPolicyType.NONE);
        if (!this.form) {
            this.form = new FormGroup({
                submissionLimit: new FormControl({ value: submissionPolicy?.submissionLimit, disabled: !this.editable() }, [
                    Validators.pattern(this.submissionLimitPattern),
                    Validators.required,
                ]),
                exceedingPenalty: new FormControl({ value: submissionPolicy?.exceedingPenalty, disabled: !this.editable() }, [
                    Validators.pattern(this.exceedingPenaltyPattern),
                    Validators.required,
                ]),
            });
            this.submissionLimitControl = this.form.get('submissionLimit')! as FormControl;
            this.exceedingPenaltyControl = this.form.get('exceedingPenalty')! as FormControl;
        } else {
            // Boxed reset values also re-apply the disabled state: on an exercise switch the editable input
            // can differ from the exercise the form was created for, and a control that stays disabled
            // reports valid, letting an incomplete policy through.
            this.form.reset({
                submissionLimit: { value: submissionPolicy?.submissionLimit, disabled: !this.editable() },
                exceedingPenalty: { value: submissionPolicy?.exceedingPenalty, disabled: !this.editable() },
            });
        }
        if (submissionPolicy && submissionPolicy.type !== SubmissionPolicyType.NONE) {
            this.policyFormInitialized = true;
        }
    }

    private setAuxiliaryBooleansOnSubmissionPolicyChange(submissionPolicyType: SubmissionPolicyType) {
        this.isNonePolicy.set(false);
        this.isLockRepositoryPolicy = this.isSubmissionPenaltyPolicy = false;
        switch (submissionPolicyType) {
            case SubmissionPolicyType.NONE:
                this.isNonePolicy.set(true);
                break;
            case SubmissionPolicyType.LOCK_REPOSITORY:
                this.isLockRepositoryPolicy = true;
                break;
            case SubmissionPolicyType.SUBMISSION_PENALTY:
                this.isSubmissionPenaltyPolicy = true;
                break;
        }
        this.selectedSubmissionPolicyType.set(submissionPolicyType);
    }

    onSubmissionPolicyTypeChanged(submissionPolicyType: SubmissionPolicyType) {
        // A policy type the user picked must survive later programmingExercise emissions. Without an
        // exercise the pick applies to nothing, so it must not set the latch either: a latch without a
        // form would block the form from ever initializing once the exercise arrives.
        if (this.programmingExercise()) {
            this.policyFormInitialized = true;
        }
        return this.applySubmissionPolicyType(submissionPolicyType);
    }

    private applySubmissionPolicyType(submissionPolicyType: SubmissionPolicyType) {
        const programmingExercise = this.programmingExercise();
        if (!programmingExercise) {
            return submissionPolicyType;
        }
        const previousSubmissionPolicyType = programmingExercise.submissionPolicy?.type ?? SubmissionPolicyType.NONE;
        if (submissionPolicyType === SubmissionPolicyType.NONE) {
            if (previousSubmissionPolicyType !== SubmissionPolicyType.NONE) {
                programmingExercise.submissionPolicy!.type = SubmissionPolicyType.NONE;
            } else {
                programmingExercise.submissionPolicy = undefined;
            }
        } else if (submissionPolicyType === SubmissionPolicyType.LOCK_REPOSITORY) {
            const newPolicy = new LockRepositoryPolicy();
            if (programmingExercise.submissionPolicy) {
                newPolicy.id = programmingExercise.submissionPolicy.id;
                newPolicy.active = programmingExercise.submissionPolicy.active;
                newPolicy.submissionLimit = programmingExercise.submissionPolicy.submissionLimit;
            }
            programmingExercise.submissionPolicy = newPolicy;
        } else if (submissionPolicyType === SubmissionPolicyType.SUBMISSION_PENALTY) {
            const newPolicy = new SubmissionPenaltyPolicy();
            if (programmingExercise.submissionPolicy) {
                newPolicy.id = programmingExercise.submissionPolicy.id;
                newPolicy.active = programmingExercise.submissionPolicy.active;
                newPolicy.submissionLimit = programmingExercise.submissionPolicy.submissionLimit;

                if (programmingExercise.submissionPolicy?.exceedingPenalty) {
                    newPolicy.exceedingPenalty = programmingExercise.submissionPolicy?.exceedingPenalty;
                } else if (this.exceedingPenaltyControl) {
                    // restore value when penalty has been set previously and was valid
                    if (this.exceedingPenaltyControl.invalid) {
                        this.exceedingPenaltyControl.setValue(undefined);
                    } else {
                        newPolicy.exceedingPenalty = this.exceedingPenaltyControl.value as number;
                    }
                }
            }
            programmingExercise.submissionPolicy = newPolicy;
        }
        this.setAuxiliaryBooleansOnSubmissionPolicyChange(submissionPolicyType);
        this.submissionPolicyTypeChange.emit();
        return submissionPolicyType;
    }

    /**
     * Returns whether the submission policy form is invalid.
     *
     * @returns {boolean} true if the form is invalid, false if the form is valid
     */
    get invalid(): boolean {
        const programmingExercise = this.programmingExercise();
        if (!programmingExercise || !this.form) {
            // The parent can bind this getter before the exercise input is written (grading page);
            // block updates until the form exists.
            return true;
        }
        const type = programmingExercise.submissionPolicy?.type;
        if (!type || type === SubmissionPolicyType.NONE) {
            return false;
        }
        return this.submissionLimitControl.invalid || (type === SubmissionPolicyType.SUBMISSION_PENALTY && this.exceedingPenaltyControl.invalid);
    }

    /**
     * Ensures synchronization between the submission policy model and the input controller, since
     * using ngModel with reactive forms has been deprecated in Angular v6
     */
    updateSubmissionLimit() {
        this.programmingExercise()!.submissionPolicy!.submissionLimit = this.submissionLimitControl.value as number;
    }

    /**
     * Ensures synchronization between the submission policy model and the input controller, since
     * using ngModel with reactive forms has been deprecated in Angular v6
     */
    updateExceedingPenalty() {
        this.programmingExercise()!.submissionPolicy!.exceedingPenalty = this.exceedingPenaltyControl.value as number;
    }
}
