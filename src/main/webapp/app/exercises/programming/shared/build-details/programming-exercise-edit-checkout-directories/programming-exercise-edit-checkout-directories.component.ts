import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import type { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { BuildPlanCheckoutDirectoriesDTO } from 'app/entities/build-plan-checkout-directories-dto';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-programming-exercise-edit-checkout-directories',
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    templateUrl: './programming-exercise-edit-checkout-directories.component.html',
})
export class ProgrammingExerciseEditCheckoutDirectoriesComponent implements OnInit, OnChanges {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() submissionBuildPlanCheckoutRepositories: BuildPlanCheckoutDirectoriesDTO;
    @Input() buildConfigCheckoutPaths: BuildPlanCheckoutDirectoriesDTO;
    @Output() buildConfigCheckoutPathsEvent = new EventEmitter<BuildPlanCheckoutDirectoriesDTO>();

    assignmentCheckoutPath: string;
    testCheckoutPath: string;
    solutionCheckoutPath: string;

    isAssigmentRepositoryEditable: boolean = false;
    isTestRepositoryEditable: boolean = false;
    isSolutionRepositoryEditable: boolean = false;

    ngOnInit() {
        this.reset();
    }

    ngOnChanges(changes: SimpleChanges) {
        console.log(changes.programmingExercise);
        const isProgrammingLanguageUpdated =
            changes.programmingExerciseCreationConfig?.currentValue?.selectedProgrammingLanguage !==
            changes.programmingExerciseCreationConfig?.previousValue?.selectedProgrammingLanguage;
        if (isProgrammingLanguageUpdated) {
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

    onAssigmentRepositoryCheckoutPathChange(event: any) {
        this.programmingExercise.buildConfig!.assignmentCheckoutPath = event.target.value;
        this.emitBuildConfigCheckoutPathsEvent();
    }

    onTestRepositoryCheckoutPathChange(event: any) {
        this.programmingExercise.buildConfig!.testCheckoutPath = event.target.value;
        this.emitBuildConfigCheckoutPathsEvent();
    }

    onSolutionRepositoryCheckoutPathChange(event: any) {
        this.programmingExercise.buildConfig!.solutionCheckoutPath = event.target.value;
        this.emitBuildConfigCheckoutPathsEvent();
    }

    private emitBuildConfigCheckoutPathsEvent() {
        this.buildConfigCheckoutPathsEvent.emit({
            solutionCheckoutDirectory: this.programmingExercise.buildConfig!.solutionCheckoutPath,
            testCheckoutDirectory: this.programmingExercise.buildConfig!.testCheckoutPath || '/',
            exerciseCheckoutDirectory: this.programmingExercise.buildConfig!.assignmentCheckoutPath,
        });
    }
}
