import { Component, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise, ProjectType } from 'app/entities/programming-exercise.model';
import { InfrastructureInputs } from '../wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-infrastructure',
    templateUrl: './programming-exercise-infrastructure.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseInfrastructureComponent {
    readonly ProjectType = ProjectType;

    @Input() isImportFromExistingExercise: boolean;
    @Input() isEdit: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() shouldHidePreview = false;
    @Input() infrastructureInputs: InfrastructureInputs;
    @Input() auxiliaryRepositoriesSupported = false;

    @Input() sequentialTestRunsAllowed: boolean;
    @Input() checkoutSolutionRepositoryAllowed: boolean;
    @Input() validIdeSelection: () => boolean | undefined;
    @Input() selectedProjectType: ProjectType;
    @Input() recreateBuildPlans: boolean;
    @Input() recreateBuildPlanOrUpdateTemplateChange: () => void;
    @Input() updateTemplate: boolean;
    @Input() testwiseCoverageAnalysisSupported: boolean;
    @Input() publishBuildPlanUrlAllowed: boolean;

    faQuestionCircle = faQuestionCircle;
}
