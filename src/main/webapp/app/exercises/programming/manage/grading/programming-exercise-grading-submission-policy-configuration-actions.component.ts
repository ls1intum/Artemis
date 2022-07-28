import { Component, EventEmitter, Input, Output } from '@angular/core';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ButtonType } from 'app/shared/components/button.component';

/**
 * The actions of the submission policy configuration:
 * - Update Submission Policy
 * - Enable/Disable Submission Policy
 */
@Component({
    selector: 'jhi-programming-exercise-grading-submission-policy-configuration-actions',
    template: `
        <div align="right">
            <jhi-button
                *ngIf="exercise.isAtLeastInstructor"
                [btnType]="ButtonType.PRIMARY"
                [title]="'artemisApp.programmingExercise.submissionPolicy.updateButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.updateButton.tooltip'"
                (onClick)="onUpdate.emit()"
                [icon]="faSave"
                [disabled]="isSaving || exercise.submissionPolicy == undefined || (exercise.submissionPolicy.type === SubmissionPolicyType.NONE && !hadPolicyBefore) || formInvalid"
            ></jhi-button>
            <jhi-button
                *ngIf="exercise.isAtLeastInstructor && hadPolicyBefore && exercise.submissionPolicy!.active"
                [btnType]="ButtonType.ERROR"
                [title]="'artemisApp.programmingExercise.submissionPolicy.deactivateButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.deactivateButton.tooltip'"
                (onClick)="onToggle.emit()"
                [disabled]="isSaving"
            ></jhi-button>
            <jhi-button
                *ngIf="exercise.isAtLeastInstructor && hadPolicyBefore && !exercise.submissionPolicy!.active"
                [btnType]="ButtonType.SUCCESS"
                [title]="'artemisApp.programmingExercise.submissionPolicy.activateButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.activateButton.tooltip'"
                (onClick)="onToggle.emit()"
                [disabled]="isSaving"
            ></jhi-button>
        </div>
    `,
})
export class ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent {
    readonly ButtonType = ButtonType;
    readonly SubmissionPolicyType = SubmissionPolicyType;

    @Input() exercise: ProgrammingExercise;
    @Input() isSaving: boolean;
    @Input() formInvalid: boolean;
    @Input() hadPolicyBefore: boolean;

    @Output() onUpdate = new EventEmitter();
    @Output() onToggle = new EventEmitter();

    // Icons
    faSave = faSave;
}
