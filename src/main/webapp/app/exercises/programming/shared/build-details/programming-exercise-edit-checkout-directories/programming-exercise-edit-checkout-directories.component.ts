import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/entities/programming/build-plan-checkout-directories-dto';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { Subject, Subscription } from 'rxjs';
import { NgModel } from '@angular/forms';

@Component({
    selector: 'jhi-programming-exercise-edit-checkout-directories',
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    templateUrl: './programming-exercise-edit-checkout-directories.component.html',
    styleUrls: ['../../../manage/programming-exercise-form.scss'],
})
export class ProgrammingExerciseEditCheckoutDirectoriesComponent implements OnChanges, OnDestroy {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingLanguage?: ProgrammingLanguage;
    @Input() pattern: RegExp;
    @Input() submissionBuildPlanCheckoutRepositories: BuildPlanCheckoutDirectoriesDTO;
    @Output() assignmentCheckoutPathEvent = new EventEmitter<string>();
    @Output() testCheckoutPathEvent = new EventEmitter<string>();
    @Output() solutionCheckoutPathEvent = new EventEmitter<string>();

    assignmentCheckoutPath: string;
    testCheckoutPath: string;
    solutionCheckoutPath: string;

    isAssigmentRepositoryEditable: boolean = false;
    isTestRepositoryEditable: boolean = false;
    isSolutionRepositoryEditable: boolean = false;

    formValid: boolean = true;
    formValidChanges = new Subject();

    // subscriptions
    fieldAssignmentSubscription?: Subscription;
    fieldTestSubscription?: Subscription;
    fieldSolutionSubscription?: Subscription;

    @ViewChild('field_assignmentRepositoryCheckoutPath') field_assignmentRepositoryCheckoutPath: NgModel;
    @ViewChild('field_testRepositoryCheckoutPath') field_testRepositoryCheckoutPath?: NgModel;
    @ViewChild('field_solutionRepositoryCheckoutPath') field_solutionRepositoryCheckoutPath?: NgModel;

    ngOnChanges(changes: SimpleChanges) {
        const isSubmissionBuildPlanCheckoutRepositoriesChanged = this.isSubmissionBuildPlanCheckoutRepositoriesChanged(
            changes.submissionBuildPlanCheckoutRepositories?.currentValue,
            changes.submissionBuildPlanCheckoutRepositories?.previousValue,
            changes.submissionBuildPlanCheckoutRepositories?.firstChange,
        );
        const isProgrammingLanguageUpdated = changes.programmingLanguage?.currentValue !== changes.programmingLanguage?.previousValue;
        if (isSubmissionBuildPlanCheckoutRepositoriesChanged || isProgrammingLanguageUpdated) {
            this.reset();
        }
    }

    ngOnDestroy() {
        this.fieldAssignmentSubscription?.unsubscribe();
        this.fieldTestSubscription?.unsubscribe();
        this.fieldSolutionSubscription?.unsubscribe();
    }

    reset() {
        const submissionBuildPlan = this.submissionBuildPlanCheckoutRepositories;
        this.isAssigmentRepositoryEditable =
            !!submissionBuildPlan?.exerciseCheckoutDirectory && submissionBuildPlan?.exerciseCheckoutDirectory !== '' && submissionBuildPlan?.exerciseCheckoutDirectory !== '/';
        if (this.isAssigmentRepositoryEditable) {
            this.assignmentCheckoutPath =
                this.programmingExercise.buildConfig?.assignmentCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.exerciseCheckoutDirectory) || '';
        } else {
            this.assignmentCheckoutPath = '';
        }
        this.isTestRepositoryEditable =
            !!submissionBuildPlan?.testCheckoutDirectory && submissionBuildPlan?.testCheckoutDirectory !== '' && submissionBuildPlan?.testCheckoutDirectory !== '/';
        if (this.isTestRepositoryEditable) {
            this.testCheckoutPath = this.programmingExercise.buildConfig?.testCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.testCheckoutDirectory) || '';
        } else {
            this.testCheckoutPath = '/';
        }
        this.isSolutionRepositoryEditable =
            !!submissionBuildPlan?.solutionCheckoutDirectory && submissionBuildPlan?.solutionCheckoutDirectory !== '' && submissionBuildPlan?.solutionCheckoutDirectory !== '/';
        if (this.isSolutionRepositoryEditable) {
            this.solutionCheckoutPath = this.programmingExercise.buildConfig?.solutionCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.solutionCheckoutDirectory) || '';
        } else {
            this.solutionCheckoutPath = '';
        }
    }

    onAssigmentRepositoryCheckoutPathChange(event: string) {
        this.assignmentCheckoutPath = event;
        this.assignmentCheckoutPathEvent.emit(this.assignmentCheckoutPath);
        this.calculateFormValid();
    }

    onTestRepositoryCheckoutPathChange(event: string) {
        this.testCheckoutPath = event;
        this.testCheckoutPathEvent.emit(this.testCheckoutPath);
        this.calculateFormValid();
    }

    onSolutionRepositoryCheckoutPathChange(event: string) {
        this.solutionCheckoutPath = event;
        this.solutionCheckoutPathEvent.emit(this.solutionCheckoutPath);
        this.calculateFormValid();
    }

    isSubmissionBuildPlanCheckoutRepositoriesChanged(newValue?: BuildPlanCheckoutDirectoriesDTO, oldValue?: BuildPlanCheckoutDirectoriesDTO, firstChange = false): boolean {
        return (
            firstChange ||
            (oldValue !== undefined && newValue?.exerciseCheckoutDirectory !== oldValue?.exerciseCheckoutDirectory) ||
            newValue?.testCheckoutDirectory !== oldValue?.testCheckoutDirectory ||
            newValue?.solutionCheckoutDirectory !== oldValue?.solutionCheckoutDirectory
        );
    }

    private removeLeadingSlash(path?: string): string | undefined {
        return path?.replace(/^\//, '');
    }

    calculateFormValid(): void {
        const isFormValid = Boolean(
            (!this.field_assignmentRepositoryCheckoutPath || this.field_assignmentRepositoryCheckoutPath?.valid) &&
                (!this.field_testRepositoryCheckoutPath || this.field_testRepositoryCheckoutPath?.valid) &&
                (!this.field_solutionRepositoryCheckoutPath || this.field_solutionRepositoryCheckoutPath?.valid),
        );
        this.formValid = isFormValid && this.areValuesUnique([this.assignmentCheckoutPath, this.testCheckoutPath, this.solutionCheckoutPath]);
        this.formValidChanges.next(this.formValid);
    }

    areValuesUnique(values: (string | undefined)[]): boolean {
        const filteredValues = values.filter((value): value is string => value !== undefined && value !== '');
        const uniqueValues = new Set(filteredValues);
        return filteredValues.length === uniqueValues.size;
    }
}
