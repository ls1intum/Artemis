import { Component, Input, OnInit } from '@angular/core';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicy, SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-submission-policy-update',
    template: `
        <div class="form-group-narrow">
            <label class="label-narrow" jhiTranslate="artemisApp.programmingExercise.submissionPolicy.title" for="field_submissionPolicy">Submission Policy</label>
            <select
                #policy
                required
                class="form-control"
                [ngModel]="selectedSubmissionPolicyType"
                (ngModelChange)="policy.value = onSubmissionPolicyTypeChanged($event)"
                name="submissionPolicyType"
                id="field_submissionPolicy"
                [disabled]="!editable || updateExistingPolicy"
            >
                <option value="none">{{ 'artemisApp.programmingExercise.submissionPolicy.none.optionLabel' | artemisTranslate }}</option>
                <option value="lock_repository">{{ 'artemisApp.programmingExercise.submissionPolicy.lockRepository.optionLabel' | artemisTranslate }}</option>
                <option value="submission_penalty">{{ 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.optionLabel' | artemisTranslate }}</option>
            </select>
        </div>

        <div class="row" *ngIf="!isNonePolicy">
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
                            min="0"
                            max="500"
                            name="submissionLimit"
                            id="field_submissionLimit"
                            [disabled]="!editable"
                            [ngModel]="this.submissionPolicy!.submissionLimit"
                            (ngModelChange)="updateSubmissionLimit(+submissionLimitInput.value)"
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
                            min="0"
                            max="100"
                            name="submissionLimitExceededPenalty"
                            id="field_submissionLimitExceededPenalty"
                            [disabled]="!editable"
                            [ngModel]="this.submissionPolicy!.exceedingPenalty"
                            (ngModelChange)="updateExceedingPenalty(+exceedingPenaltyInput.value)"
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
})
export class SubmissionPolicyUpdateComponent implements OnInit {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() editable: boolean;
    @Input() updateExistingPolicy: boolean;

    submissionPolicy?: SubmissionPolicy;
    selectedSubmissionPolicyType: SubmissionPolicyType;

    isSubmissionPenaltyPolicy: boolean;
    isLockRepositoryPolicy: boolean;
    isNonePolicy: boolean;

    hadSubmissionPolicyBefore: boolean;

    ngOnInit(): void {
        this.submissionPolicy = this.programmingExercise!.submissionPolicy;
        this.hadSubmissionPolicyBefore = this.submissionPolicy != undefined;
        this.onSubmissionPolicyTypeChanged(this.submissionPolicy?.type ?? SubmissionPolicyType.NONE);
        if (!this.isNonePolicy) {
            this.updateSubmissionLimit(this.submissionPolicy?.submissionLimit ?? 0);
            if (this.isSubmissionPenaltyPolicy) {
                this.updateExceedingPenalty((this.submissionPolicy as SubmissionPenaltyPolicy).exceedingPenalty ?? 0);
            }
        }
        console.log('SubmissionPolicy: ' + this.programmingExercise + ', ' + this.editable);
    }

    updateSubmissionLimit(limit: number) {
        this.submissionPolicy!.submissionLimit = limit;
        this.linkPolicyToExercise();
        return limit;
    }

    updateExceedingPenalty(penalty: number) {
        this.submissionPolicy!.exceedingPenalty = penalty;
        this.linkPolicyToExercise();
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
            this.submissionPolicy = undefined;
        } else if (submissionPolicyType === SubmissionPolicyType.LOCK_REPOSITORY) {
            const newPolicy = new LockRepositoryPolicy();
            if (this.submissionPolicy) {
                newPolicy.id = this.submissionPolicy.id;
                newPolicy.active = this.submissionPolicy.active;
                newPolicy.submissionLimit = this.submissionPolicy.submissionLimit;
            }
            this.submissionPolicy = newPolicy;
        } else if (submissionPolicyType === SubmissionPolicyType.SUBMISSION_PENALTY) {
            const newPolicy = new SubmissionPenaltyPolicy();
            if (this.submissionPolicy) {
                newPolicy.id = this.submissionPolicy.id;
                newPolicy.active = this.submissionPolicy.active;
                newPolicy.submissionLimit = this.submissionPolicy.submissionLimit;
                if (previousSubmissionPolicyType === SubmissionPolicyType.SUBMISSION_PENALTY) {
                    newPolicy.exceedingPenalty = this.submissionPolicy.exceedingPenalty;
                }
            }
            this.submissionPolicy = newPolicy;
        }
        this.setAuxiliaryBooleansOnSubmissionPolicyChange(submissionPolicyType);
        this.linkPolicyToExercise();
        return submissionPolicyType!;
    }

    private linkPolicyToExercise(): void {
        this.programmingExercise.submissionPolicy = this.submissionPolicy;
    }
}
