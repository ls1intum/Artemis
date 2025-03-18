import { Component, Input } from '@angular/core';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';

@Component({
    selector: 'jhi-orion-assessment-instructions',
    template: `
        <jhi-assessment-instructions [exercise]="exercise" [programmingParticipation]="programmingParticipation" [readOnly]="readOnly">
            <ng-template #overrideTitle />
        </jhi-assessment-instructions>
    `,
    imports: [AssessmentInstructionsComponent],
})
export class OrionAssessmentInstructionsComponent {
    @Input() readOnly: boolean;
    @Input() programmingParticipation?: ProgrammingExerciseStudentParticipation;
    @Input() exercise: Exercise;
}
