import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { IncludedInOverallScore } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-programming-exercise-update-wizard-grading',
    templateUrl: './programming-exercise-update-wizard-grading.component.html',
    styleUrls: ['./programming-exercise-update-wizard.component.scss'],
})
export class ProgrammingExerciseUpdateWizardGradingComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;

    @Input() staticCodeAnalysisAllowed: boolean;
    @Input() onStaticCodeAnalysisChanged: () => void;
    @Input() maxPenaltyPattern: string;

    faQuestionCircle = faQuestionCircle;
}
