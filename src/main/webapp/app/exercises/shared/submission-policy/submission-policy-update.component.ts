import { Component, Input, OnInit } from '@angular/core';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-submission-policy-update',
    template: `
        <div class="form-group-narrow">
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

        <div class="row mb-3" *ngIf="!isNonePolicy">
            <div class="col">
                <ng-container>
                    <label class="label-narrow" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle" for="field_submissionLimitExceededPenalty"
                        >Submission Limit</label
                    >
                    <fa-icon
                        icon="question-circle"
                        class="text-secondary"
                        placement="top"
                        ngbTooltip="{{ 'artemisApp.programmingExercise.submissionPolicy.submissionLimitDescription' | artemisTranslate }}"
                    ></fa-icon>
                    <div class="input-group">
                        <input
                            #submissionLimitInput
                            required
                            type="number"
                            class="form-control"
                            step="1"
                            name="submissionLimit"
                            id="field_submissionLimit"
                            [pattern]="submissionLimitPattern"
                            [disabled]="!editable"
                            [ngModel]="this.programmingExercise.submissionPolicy!.submissionLimit"
                            (ngModelChange)="updateSubmissionLimit(submissionLimitInput.value)"
                            #penalty="ngModel"
                        />
                    </div>
                    <ng-container *ngFor="let e of penalty.errors! | keyvalue">
                        <div *ngIf="penalty.invalid && (penalty.dirty || penalty.touched)" class="alert alert-danger">
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
                        >Penalty after Exceeding Submission Limit</label
                    >
                    <fa-icon
                        icon="question-circle"
                        class="text-secondary"
                        placement="top"
                        ngbTooltip="{{ 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.exceedingLimitDescription' | artemisTranslate }}"
                    ></fa-icon>
                    <div class="input-group">
                        <div class="input-group-prepend">
                            <span class="input-group-text">%</span>
                        </div>
                        <input
                            #exceedingPenaltyInput
                            required
                            type="number"
                            class="form-control"
                            [customMin]="0"
                            name="submissionLimitExceededPenalty"
                            id="field_submissionLimitExceededPenalty"
                            [disabled]="!editable"
                            [ngModel]="this.programmingExercise.submissionPolicy!.exceedingPenalty"
                            (ngModelChange)="updateExceedingPenalty(exceedingPenaltyInput.value)"
                            #penalty="ngModel"
                        />
                    </div>
                    <ng-container *ngFor="let e of penalty.errors! | keyvalue">
                        <div *ngIf="penalty.invalid && (penalty.dirty || penalty.touched)" class="alert alert-danger">
                            <div [jhiTranslate]="'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning' + '.' + e.key"></div>
                        </div>
                    </ng-container>
                </ng-container>
            </div>
        </div>
    `,
    styleUrls: ['../../programming/manage/programming-exercise-form.scss'],
})
export class SubmissionPolicyUpdateComponent implements OnInit {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() editable: boolean;

    selectedSubmissionPolicyType: SubmissionPolicyType;

    isSubmissionPenaltyPolicy: boolean;
    isLockRepositoryPolicy: boolean;
    isNonePolicy: boolean;

    // This is used to ensure that only integers [1-500] can be used as input for the submission limit.
    submissionLimitPattern = '^([1-9]|([1-9][0-9])|([1-4][0-9][0-9])|500)$';

    ngOnInit(): void {
        this.onSubmissionPolicyTypeChanged(this.programmingExercise.submissionPolicy?.type ?? SubmissionPolicyType.NONE);
        if (!this.isNonePolicy) {
            this.updateSubmissionLimit(String(this.programmingExercise.submissionPolicy!.submissionLimit ?? 5));
            if (this.isSubmissionPenaltyPolicy) {
                this.updateExceedingPenalty(String((this.programmingExercise.submissionPolicy as SubmissionPenaltyPolicy).exceedingPenalty ?? 10));
            }
        }
    }

    updateSubmissionLimit(limit: string) {
        this.programmingExercise.submissionPolicy!.submissionLimit = +limit;
        return limit;
    }

    updateExceedingPenalty(penalty: string) {
        this.programmingExercise.submissionPolicy!.exceedingPenalty = +penalty;
        return penalty;
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
                if (this.programmingExercise.submissionPolicy.exceedingPenalty) {
                    newPolicy.exceedingPenalty = this.programmingExercise.submissionPolicy.exceedingPenalty;
                }
            }
            this.programmingExercise.submissionPolicy = newPolicy;
        }
        this.setAuxiliaryBooleansOnSubmissionPolicyChange(submissionPolicyType);
        return submissionPolicyType!;
    }
}
