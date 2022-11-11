import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-problem',
    templateUrl: './programming-exercise-update-wizard-problem.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardProblemComponent {
    readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ProjectType = ProjectType;
    readonly AssessmentType = AssessmentType;

    programmingExercise: ProgrammingExercise;

    @Input() isImport: boolean;

    @Input() problemStatementLoaded: boolean;
    @Input() templateParticipationResultLoaded: boolean;
    @Input() hasUnsavedChanges: boolean;
    @Input() rerenderSubject: Observable<void>;
    @Input() sequentialTestRunsAllowed: boolean;
    @Input() checkoutSolutionRepositoryAllowed: boolean;
    @Input() validIdeSelection: () => boolean | undefined;
    @Input() selectedProjectType: ProjectType;
    @Input() inProductionEnvironment: boolean;
    @Input() recreateBuildPlans: boolean;
    @Input() onRecreateBuildPlanOrUpdateTemplateChange: () => void;
    @Input() updateTemplate: boolean;

    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();

    @Input()
    get exercise() {
        return this.programmingExercise;
    }

    set exercise(exercise: ProgrammingExercise) {
        console.log('Set EXERCISE');
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }

    faQuestionCircle = faQuestionCircle;
}
