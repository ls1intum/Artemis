import { Component, effect, input, output, viewChild } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';

import { Subject } from 'rxjs';
import { FormsModule, NgModel } from '@angular/forms';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-exercise-edit-checkout-directories',
    imports: [HelpIconComponent, TranslateDirective, FormsModule],
    templateUrl: './programming-exercise-edit-checkout-directories.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseEditCheckoutDirectoriesComponent {
    programmingExercise = input.required<ProgrammingExercise>();
    pattern = input.required<RegExp>();
    submissionBuildPlanCheckoutRepositories = input.required<BuildPlanCheckoutDirectoriesDTO>();
    assignmentCheckoutPathEvent = output<string>();
    testCheckoutPathEvent = output<string>();
    solutionCheckoutPathEvent = output<string>();

    assignmentCheckoutPath: string;
    testCheckoutPath: string;
    solutionCheckoutPath: string;

    isAssigmentRepositoryEditable = false;
    isTestRepositoryEditable = false;
    isSolutionRepositoryEditable = false;

    formValid = true;
    formValidChanges = new Subject();

    field_assignmentRepositoryCheckoutPath = viewChild<NgModel>('field_assignmentRepositoryCheckoutPath');
    field_testRepositoryCheckoutPath = viewChild<NgModel>('field_testRepositoryCheckoutPath');
    field_solutionRepositoryCheckoutPath = viewChild<NgModel>('field_solutionRepositoryCheckoutPath');

    constructor() {
        effect(() => {
            this.reset();
        });
    }

    reset() {
        const submissionBuildPlan = this.submissionBuildPlanCheckoutRepositories();
        this.isAssigmentRepositoryEditable =
            !!submissionBuildPlan?.exerciseCheckoutDirectory &&
            submissionBuildPlan?.exerciseCheckoutDirectory.trim() !== '' &&
            submissionBuildPlan?.exerciseCheckoutDirectory !== '/';
        if (this.isAssigmentRepositoryEditable) {
            this.assignmentCheckoutPath =
                this.programmingExercise().buildConfig?.assignmentCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.exerciseCheckoutDirectory) || '';
        } else {
            this.assignmentCheckoutPath = '';
        }
        this.isTestRepositoryEditable =
            !!submissionBuildPlan?.testCheckoutDirectory && submissionBuildPlan?.testCheckoutDirectory.trim() !== '' && submissionBuildPlan?.testCheckoutDirectory !== '/';
        if (this.isTestRepositoryEditable) {
            this.testCheckoutPath = this.programmingExercise().buildConfig?.testCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.testCheckoutDirectory) || '';
        } else {
            this.testCheckoutPath = '/';
        }
        this.isSolutionRepositoryEditable =
            !!submissionBuildPlan?.solutionCheckoutDirectory &&
            submissionBuildPlan?.solutionCheckoutDirectory.trim() !== '' &&
            submissionBuildPlan?.solutionCheckoutDirectory !== '/';
        if (this.isSolutionRepositoryEditable) {
            this.solutionCheckoutPath =
                this.programmingExercise().buildConfig?.solutionCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.solutionCheckoutDirectory) || '';
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

    private removeLeadingSlash(path?: string): string | undefined {
        return path?.replace(/^\//, '');
    }

    calculateFormValid(): void {
        const isFormValid = Boolean(
            (!this.field_assignmentRepositoryCheckoutPath() || this.field_assignmentRepositoryCheckoutPath()?.valid) &&
            (!this.field_testRepositoryCheckoutPath() || this.field_testRepositoryCheckoutPath()?.valid) &&
            (!this.field_solutionRepositoryCheckoutPath() || this.field_solutionRepositoryCheckoutPath()?.valid),
        );
        this.formValid = isFormValid && this.areValuesUnique([this.assignmentCheckoutPath, this.testCheckoutPath, this.solutionCheckoutPath]);
        this.formValidChanges.next(this.formValid);
    }

    areValuesUnique(values: (string | undefined)[]): boolean {
        const filteredValues = values.filter((value): value is string => value !== undefined && value.trim() !== '');
        const uniqueValues = new Set(filteredValues);
        return filteredValues.length === uniqueValues.size;
    }
}
