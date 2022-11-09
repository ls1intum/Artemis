import { Component, Input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-problem',
    templateUrl: './programming-exercise-update-wizard-problem.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardProblemComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;
    readonly AssessmentType = AssessmentType;

    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() problemStatementLoaded: boolean;
    @Input() templateParticipationResultLoaded: boolean;
    @Input() hasUnsavedChanges: boolean;
    @Input() rerenderSubject: Subject<void>;
    @Input() sequentialTestRunsAllowed: boolean;
    @Input() checkoutSolutionRepositoryAllowed: boolean;
    @Input() validIdeSelection: () => boolean | undefined;
    @Input() selectedProjectType: ProjectType;
    @Input() inProductionEnvironment: boolean;
    @Input() recreateBuildPlans: boolean;
    @Input() onRecreateBuildPlanOrUpdateTemplateChange: () => void;
    @Input() updateTemplate: boolean;

    faQuestionCircle = faQuestionCircle;
}
