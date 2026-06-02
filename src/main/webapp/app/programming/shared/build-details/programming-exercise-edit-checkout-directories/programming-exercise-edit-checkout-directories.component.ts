import { Component, computed, input, linkedSignal, output, viewChild } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { BuildPlanCheckoutDirectoriesDTO } from 'app/programming/shared/entities/build-plan-checkout-directories-dto';

import { Subject } from 'rxjs';
import { FormsModule, NgModel } from '@angular/forms';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

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

    // Editability is fully derived from the submissionBuildPlan input.
    readonly isAssigmentRepositoryEditable = computed(() => this.isEditable(this.submissionBuildPlanCheckoutRepositories()?.exerciseCheckoutDirectory));
    readonly isTestRepositoryEditable = computed(() => this.isEditable(this.submissionBuildPlanCheckoutRepositories()?.testCheckoutDirectory));
    readonly isSolutionRepositoryEditable = computed(() => this.isEditable(this.submissionBuildPlanCheckoutRepositories()?.solutionCheckoutDirectory));

    // Path values re-seed from the inputs whenever those change (replacing the legacy effect chain),
    // while remaining writable for user edits via the ngModelChange handlers below.
    readonly assignmentCheckoutPath = linkedSignal<string>(() => {
        if (!this.isAssigmentRepositoryEditable()) {
            return '';
        }
        const submissionBuildPlan = this.submissionBuildPlanCheckoutRepositories();
        return this.programmingExercise().buildConfig?.assignmentCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.exerciseCheckoutDirectory) || '';
    });
    readonly testCheckoutPath = linkedSignal<string>(() => {
        if (!this.isTestRepositoryEditable()) {
            return '/';
        }
        const submissionBuildPlan = this.submissionBuildPlanCheckoutRepositories();
        return this.programmingExercise().buildConfig?.testCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.testCheckoutDirectory) || '';
    });
    readonly solutionCheckoutPath = linkedSignal<string>(() => {
        if (!this.isSolutionRepositoryEditable()) {
            return '';
        }
        const submissionBuildPlan = this.submissionBuildPlanCheckoutRepositories();
        return this.programmingExercise().buildConfig?.solutionCheckoutPath || this.removeLeadingSlash(submissionBuildPlan?.solutionCheckoutDirectory) || '';
    });

    formValid = true;
    formValidChanges = new Subject();

    field_assignmentRepositoryCheckoutPath = viewChild<NgModel>('field_assignmentRepositoryCheckoutPath');
    field_testRepositoryCheckoutPath = viewChild<NgModel>('field_testRepositoryCheckoutPath');
    field_solutionRepositoryCheckoutPath = viewChild<NgModel>('field_solutionRepositoryCheckoutPath');

    onAssigmentRepositoryCheckoutPathChange(event: string) {
        this.assignmentCheckoutPath.set(event);
        this.assignmentCheckoutPathEvent.emit(event);
        this.calculateFormValid();
    }

    onTestRepositoryCheckoutPathChange(event: string) {
        this.testCheckoutPath.set(event);
        this.testCheckoutPathEvent.emit(event);
        this.calculateFormValid();
    }

    onSolutionRepositoryCheckoutPathChange(event: string) {
        this.solutionCheckoutPath.set(event);
        this.solutionCheckoutPathEvent.emit(event);
        this.calculateFormValid();
    }

    private removeLeadingSlash(path?: string): string | undefined {
        return path?.replace(/^\//, '');
    }

    private isEditable(directory?: string): boolean {
        return !!directory && directory.trim() !== '' && directory !== '/';
    }

    calculateFormValid(): void {
        const isFormValid = Boolean(
            (!this.field_assignmentRepositoryCheckoutPath() || this.field_assignmentRepositoryCheckoutPath()?.valid) &&
            (!this.field_testRepositoryCheckoutPath() || this.field_testRepositoryCheckoutPath()?.valid) &&
            (!this.field_solutionRepositoryCheckoutPath() || this.field_solutionRepositoryCheckoutPath()?.valid),
        );
        this.formValid = isFormValid && this.areValuesUnique([this.assignmentCheckoutPath(), this.testCheckoutPath(), this.solutionCheckoutPath()]);
        this.formValidChanges.next(this.formValid);
    }

    areValuesUnique(values: (string | undefined)[]): boolean {
        const filteredValues = values.filter((value): value is string => value !== undefined && value.trim() !== '');
        const uniqueValues = new Set(filteredValues);
        return filteredValues.length === uniqueValues.size;
    }
}
