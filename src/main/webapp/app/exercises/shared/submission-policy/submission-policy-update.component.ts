import { Component, Input, OnInit } from '@angular/core';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { FormControl, FormGroup, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-submission-policy-update',
    template: `
        <div class="form-group-narrow mb-3">
            <label class="label-narrow" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.title" for="field_submissionPolicy">Submission Policy</label>
            <select
                #policy
                required
                class="form-select"
                [ngModel]="selectedSubmissionPolicyType"
                (ngModelChange)="policy.value = onSubmissionPolicyTypeChanged($event)"
                name="submissionPolicyType"
                id="field_submissionPolicy"
                [disabled]="!editable"
            >
                <option value="none">{{ 'artemisApp.programmingExercise.submissionPolicy.none.optionLabel' | artemisTranslate }}</option>
                <option value="lock_repository">{{ 'artemisApp.programmingExercise.submissionPolicy.lockRepository.optionLabel' | artemisTranslate }}</option>
                <option value="submission_penalty">{{ 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.optionLabel' | artemisTranslate }}</option>
            </select>
        </div>
        <form [formGroup]="form" *ngIf="!isNonePolicy">
            <div class="row mb-3">
                <div class="col">
                    <ng-container>
                        <label class="label-narrow" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle" for="field_submissionLimitExceededPenalty"
                            >Submission limit</label
                        >
                        <jhi-help-icon placement="top" text="artemisApp.programmingExercise.submissionPolicy.submissionLimitDescription"></jhi-help-icon>
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
                        <ng-container *ngFor="let e of submissionLimitControl.errors! | keyvalue">
                            <div *ngIf="submissionLimitControl.invalid && (submissionLimitControl.dirty || submissionLimitControl.touched)" class="alert alert-danger">
                                <div [jhiTranslate]="'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning' + '.' + e.key"></div>
                            </div>
                        </ng-container>
                    </ng-container>
                </div>
                <div class="col">
                    <ng-container *ngIf="this.isSubmissionPenaltyPolicy">
                        <label
                            class="label-narrow"
                            jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldTitle"
                            for="field_submissionLimitExceededPenalty"
                            >Penalty after Exceeding Submission limit</label
                        >
                        <jhi-help-icon placement="top" text="artemisApp.programmingExercise.submissionPolicy.submissionPenalty.exceedingLimitDescription"></jhi-help-icon>
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
                        <ng-container *ngFor="let e of exceedingPenaltyControl.errors! | keyvalue">
                            <div *ngIf="exceedingPenaltyControl.invalid && (exceedingPenaltyControl.dirty || exceedingPenaltyControl.touched)" class="alert alert-danger">
                                <div [jhiTranslate]="'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning' + '.' + e.key"></div>
                            </div>
                        </ng-container>
                    </ng-container>
                </div>
            </div>
        </form>
    `,
    styleUrls: ['../../programming/manage/programming-exercise-form.scss'],
})
export class SubmissionPolicyUpdateComponent implements OnInit {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() editable: boolean;

    form: FormGroup;

    selectedSubmissionPolicyType: SubmissionPolicyType;

    isSubmissionPenaltyPolicy: boolean;
    isLockRepositoryPolicy: boolean;
    isNonePolicy: boolean;

    // This is used to ensure that only integers [1-500] can be used as input for the submission limit.
    submissionLimitPattern = '^([1-9]|([1-9][0-9])|([1-4][0-9][0-9])|500)$';

    submissionLimitControl: FormControl;
    exceedingPenaltyControl: FormControl;

    // penalty can be any (point) number greater than 0
    exceedingPenaltyPattern = RegExp('^0*[1-9][0-9]*(\\.[0-9]+)?|0+\\.[0-9]*[1-9][0-9]*$');

    ngOnInit(): void {
        this.onSubmissionPolicyTypeChanged(this.programmingExercise.submissionPolicy?.type ?? SubmissionPolicyType.NONE);
        this.form = new FormGroup({
            submissionLimit: new FormControl({ value: this.programmingExercise.submissionPolicy?.submissionLimit, disabled: !this.editable }, [
                Validators.pattern(this.submissionLimitPattern),
                Validators.required,
            ]),
            exceedingPenalty: new FormControl({ value: this.programmingExercise.submissionPolicy?.exceedingPenalty, disabled: !this.editable }, [
                Validators.pattern(this.exceedingPenaltyPattern),
                Validators.required,
            ]),
        });
        this.submissionLimitControl = this.form.get('submissionLimit')! as FormControl;
        this.exceedingPenaltyControl = this.form.get('exceedingPenalty')! as FormControl;
    }

    private setAuxiliaryBooleansOnSubmissionPolicyChange(submissionPolicyType: SubmissionPolicyType) {
        this.isNonePolicy = this.isLockRepositoryPolicy = this.isSubmissionPenaltyPolicy = false;
        switch (submissionPolicyType) {
            case SubmissionPolicyType.NONE:
                this.isNonePolicy = true;
                break;
            case SubmissionPolicyType.LOCK_REPOSITORY:
                this.isLockRepositoryPolicy = true;
                break;
            case SubmissionPolicyType.SUBMISSION_PENALTY:
                this.isSubmissionPenaltyPolicy = true;
                break;
        }
        this.selectedSubmissionPolicyType = submissionPolicyType;
    }

    onSubmissionPolicyTypeChanged(submissionPolicyType: SubmissionPolicyType) {
        const previousSubmissionPolicyType = this.programmingExercise?.submissionPolicy?.type ?? SubmissionPolicyType.NONE;
        if (submissionPolicyType === SubmissionPolicyType.NONE) {
            if (previousSubmissionPolicyType !== SubmissionPolicyType.NONE) {
                this.programmingExercise.submissionPolicy!.type = SubmissionPolicyType.NONE;
            } else {
                this.programmingExercise.submissionPolicy = undefined;
            }
        } else if (submissionPolicyType === SubmissionPolicyType.LOCK_REPOSITORY) {
            const newPolicy = new LockRepositoryPolicy();
            if (this.programmingExercise.submissionPolicy) {
                newPolicy.id = this.programmingExercise.submissionPolicy.id;
                newPolicy.active = this.programmingExercise.submissionPolicy.active;
                newPolicy.submissionLimit = this.programmingExercise.submissionPolicy.submissionLimit;
            }
            this.programmingExercise.submissionPolicy = newPolicy;
        } else if (submissionPolicyType === SubmissionPolicyType.SUBMISSION_PENALTY) {
            const newPolicy = new SubmissionPenaltyPolicy();
            if (this.programmingExercise.submissionPolicy) {
                newPolicy.id = this.programmingExercise.submissionPolicy.id;
                newPolicy.active = this.programmingExercise.submissionPolicy.active;
                newPolicy.submissionLimit = this.programmingExercise.submissionPolicy!.submissionLimit;

                if (this.programmingExercise.submissionPolicy?.exceedingPenalty) {
                    newPolicy.exceedingPenalty = this.programmingExercise.submissionPolicy?.exceedingPenalty;
                } else if (this.exceedingPenaltyControl) {
                    // restore value when penalty has been set previously and was valid
                    if (this.exceedingPenaltyControl.invalid) {
                        this.exceedingPenaltyControl.setValue(undefined);
                    } else {
                        newPolicy.exceedingPenalty = this.exceedingPenaltyControl.value as number;
                    }
                }
            }
            this.programmingExercise.submissionPolicy = newPolicy;
        }
        this.setAuxiliaryBooleansOnSubmissionPolicyChange(submissionPolicyType);
        return submissionPolicyType!;
    }

    /**
     * Returns whether the submission policy form is invalid.
     *
     * @returns {boolean} true if the form is invalid, false if the form is valid
     */
    get invalid(): boolean {
        const type = this.programmingExercise?.submissionPolicy?.type;
        if (!this.form || !type || type === SubmissionPolicyType.NONE) {
            return false;
        }
        return this.submissionLimitControl.invalid || (type === SubmissionPolicyType.SUBMISSION_PENALTY && this.exceedingPenaltyControl.invalid);
    }

    /**
     * Ensures synchronization between the submission policy model and the input controller, since
     * using ngModel with reactive forms has been deprecated in Angular v6
     */
    updateSubmissionLimit() {
        this.programmingExercise!.submissionPolicy!.submissionLimit = this.submissionLimitControl.value as number;
    }

    /**
     * Ensures synchronization between the submission policy model and the input controller, since
     * using ngModel with reactive forms has been deprecated in Angular v6
     */
    updateExceedingPenalty() {
        this.programmingExercise!.submissionPolicy!.exceedingPenalty = this.exceedingPenaltyControl.value as number;
    }
}
