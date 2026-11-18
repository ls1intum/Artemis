import { Component, input, output } from '@angular/core';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

/**
 * The actions of the submission policy configuration:
 * - Update Submission Policy
 * - Enable/Disable Submission Policy
 */
@Component({
    selector: 'jhi-programming-exercise-grading-submission-policy-configuration-actions',
    template: `
        <div align="right">
            @if (exercise().isAtLeastInstructor) {
                <jhi-button
                    [btnType]="ButtonType.PRIMARY"
                    [title]="'artemisApp.programmingExercise.submissionPolicy.updateButton.title'"
                    [tooltip]="'artemisApp.programmingExercise.submissionPolicy.updateButton.tooltip'"
                    (onClick)="onUpdate.emit()"
                    [icon]="faSave"
                    [disabled]="
                        isSaving() || !exercise().submissionPolicy || (exercise().submissionPolicy?.type === SubmissionPolicyType.NONE && !hadPolicyBefore()) || formInvalid()
                    "
                />
            }
            @if (exercise().isAtLeastInstructor && hadPolicyBefore() && exercise().submissionPolicy!.active) {
                <jhi-button
                    [btnType]="ButtonType.ERROR"
                    [title]="'artemisApp.programmingExercise.submissionPolicy.deactivateButton.title'"
                    [tooltip]="'artemisApp.programmingExercise.submissionPolicy.deactivateButton.tooltip'"
                    (onClick)="onToggle.emit()"
                    [disabled]="isSaving()"
                />
            }
            @if (exercise().isAtLeastInstructor && hadPolicyBefore() && !exercise().submissionPolicy!.active) {
                <jhi-button
                    [btnType]="ButtonType.SUCCESS"
                    [title]="'artemisApp.programmingExercise.submissionPolicy.activateButton.title'"
                    [tooltip]="'artemisApp.programmingExercise.submissionPolicy.activateButton.tooltip'"
                    (onClick)="onToggle.emit()"
                    [disabled]="isSaving()"
                />
            }
        </div>
    `,
    imports: [ButtonComponent],
})
export class ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent {
    readonly ButtonType = ButtonType;
    readonly SubmissionPolicyType = SubmissionPolicyType;

    readonly exercise = input.required<ProgrammingExercise>();
    readonly isSaving = input.required<boolean>();
    readonly formInvalid = input.required<boolean>();
    readonly hadPolicyBefore = input.required<boolean>();

    readonly onUpdate = output();
    readonly onToggle = output();

    // Icons
    faSave = faSave;
}
