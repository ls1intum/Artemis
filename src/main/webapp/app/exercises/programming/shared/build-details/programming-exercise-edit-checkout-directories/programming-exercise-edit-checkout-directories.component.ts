import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/entities/build-plan-checkout-directories-dto';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-programming-exercise-edit-checkout-directories',
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    templateUrl: './programming-exercise-edit-checkout-directories.component.html',
})
export class ProgrammingExerciseEditCheckoutDirectoriesComponent implements OnChanges {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingLanguage: ProgrammingLanguage;
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

    ngOnChanges(changes: SimpleChanges) {
        const isSubmissionBuildPlanCheckoutRepositoriesChanged = this.isSubmissionBuildPlanCheckoutRepositoriesChanged();
        const isProgrammingLanguageUpdated = !changes.programmingLanguage?.firstChange && changes.programmingLanguage?.currentValue !== changes.programmingLanguage?.previousValue;
        if (isProgrammingLanguageUpdated) {
            this.resetProgrammingExerciseBuildCheckoutPaths();
        }
        if (isSubmissionBuildPlanCheckoutRepositoriesChanged) {
            this.reset();
        }
    }

    reset() {
        const submissionBuildPlan = this.submissionBuildPlanCheckoutRepositories;
        this.isAssigmentRepositoryEditable =
            !!submissionBuildPlan?.exerciseCheckoutDirectory && submissionBuildPlan?.exerciseCheckoutDirectory !== '' && submissionBuildPlan?.exerciseCheckoutDirectory !== '/';
        if (this.isAssigmentRepositoryEditable) {
            this.assignmentCheckoutPath = this.programmingExercise.buildConfig?.assignmentCheckoutPath || submissionBuildPlan?.exerciseCheckoutDirectory || '';
        }
        this.isTestRepositoryEditable =
            !!submissionBuildPlan?.testCheckoutDirectory && submissionBuildPlan?.testCheckoutDirectory !== '' && submissionBuildPlan?.testCheckoutDirectory !== '/';
        if (this.isTestRepositoryEditable) {
            this.testCheckoutPath = this.programmingExercise.buildConfig?.testCheckoutPath || submissionBuildPlan?.testCheckoutDirectory || '';
        }
        this.isSolutionRepositoryEditable =
            !!submissionBuildPlan?.solutionCheckoutDirectory && submissionBuildPlan?.solutionCheckoutDirectory !== '' && submissionBuildPlan?.solutionCheckoutDirectory !== '/';
        if (this.isSolutionRepositoryEditable) {
            this.solutionCheckoutPath = this.programmingExercise.buildConfig?.solutionCheckoutPath || submissionBuildPlan?.solutionCheckoutDirectory || '';
        }
    }

    resetProgrammingExerciseBuildCheckoutPaths() {
        this.programmingExercise.buildConfig!.assignmentCheckoutPath = '';
        this.programmingExercise.buildConfig!.testCheckoutPath = '';
        this.programmingExercise.buildConfig!.solutionCheckoutPath = '';
    }

    onAssigmentRepositoryCheckoutPathChange(event: any) {
        this.assignmentCheckoutPath = event.target.value;
        this.assignmentCheckoutPathEvent.emit(this.assignmentCheckoutPath);
    }

    onTestRepositoryCheckoutPathChange(event: any) {
        this.testCheckoutPath = event.target.value;
        this.testCheckoutPathEvent.emit(this.testCheckoutPath);
    }

    onSolutionRepositoryCheckoutPathChange(event: any) {
        this.solutionCheckoutPath = event.target.value;
        this.solutionCheckoutPathEvent.emit(this.solutionCheckoutPath);
    }

    isSubmissionBuildPlanCheckoutRepositoriesChanged(): boolean {
        return (
            this.assignmentCheckoutPath !== this.submissionBuildPlanCheckoutRepositories?.exerciseCheckoutDirectory ||
            this.testCheckoutPath !== this.submissionBuildPlanCheckoutRepositories?.testCheckoutDirectory ||
            this.solutionCheckoutPath !== this.submissionBuildPlanCheckoutRepositories?.solutionCheckoutDirectory
        );
    }
}
