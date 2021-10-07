import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ButtonType } from 'app/shared/components/button.component';

/**
 * The actions of the submission policy configuration:
 * - Delete Submission Policy
 * - Update Submission Policy
 * - Enable/Disable Submission Policy
 */
@Component({
    selector: 'jhi-programming-exercise-grading-submission-policy-configuration-actions',
    template: `
        <div align="right">
            <jhi-button
                *ngIf="exercise.isAtLeastEditor && exercise.submissionPolicy && exercise.submissionPolicy.id != undefined"
                [btnType]="ButtonType.PRIMARY"
                [title]="'artemisApp.programmingExercise.submissionPolicy.updateButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.updateButton.tooltip'"
                (onClick)="onUpdate.emit()"
                [icon]="'save'"
                [disabled]="isSaving"
            ></jhi-button>
            <jhi-button
                *ngIf="exercise.isAtLeastInstructor && (exercise.submissionPolicy == undefined || exercise.submissionPolicy.id == undefined)"
                [btnType]="ButtonType.SUCCESS"
                [title]="'artemisApp.programmingExercise.submissionPolicy.addButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.addButton.tooltip'"
                (onClick)="onAdd.emit()"
                [icon]="'plus'"
                [disabled]="isSaving || exercise.submissionPolicy == undefined"
            ></jhi-button>
            <jhi-button
                *ngIf="exercise.isAtLeastInstructor && exercise.submissionPolicy && exercise.submissionPolicy.id != undefined && exercise.submissionPolicy!.active"
                [btnType]="ButtonType.ERROR"
                [title]="'artemisApp.programmingExercise.submissionPolicy.deactivateButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.deactivateButton.tooltip'"
                (onClick)="onToggle.emit()"
                [disabled]="isSaving"
            ></jhi-button>
            <jhi-button
                *ngIf="exercise.isAtLeastInstructor && exercise.submissionPolicy && exercise.submissionPolicy.id != undefined && !exercise.submissionPolicy!.active"
                [btnType]="ButtonType.SUCCESS"
                [title]="'artemisApp.programmingExercise.submissionPolicy.activateButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.activateButton.tooltip'"
                (onClick)="onToggle.emit()"
                [disabled]="isSaving"
            ></jhi-button>
            <jhi-button
                *ngIf="exercise.isAtLeastInstructor && exercise.submissionPolicy && exercise.submissionPolicy.id != undefined"
                [btnType]="ButtonType.ERROR"
                [title]="'artemisApp.programmingExercise.submissionPolicy.deleteButton.title'"
                [tooltip]="'artemisApp.programmingExercise.submissionPolicy.deleteButton.tooltip'"
                (onClick)="onRemove.emit()"
                [icon]="'trash'"
                [disabled]="isSaving"
            ></jhi-button>
        </div>
    `,
})
export class ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent {
    readonly ButtonType = ButtonType;

    @Input() exercise: ProgrammingExercise;
    @Input() hasUnsavedChanges: boolean;
    @Input() isSaving: boolean;

    @Output() onUpdate = new EventEmitter();
    @Output() onToggle = new EventEmitter();
    @Output() onRemove = new EventEmitter();
    @Output() onAdd = new EventEmitter();
}
