import { Component, Input } from '@angular/core';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-orion-assessment-instructions',
    template: `
        <jhi-assessment-instructions [exercise]="exercise" [programmingParticipation]="programmingParticipation" [readOnly]="readOnly">
            <ng-template #overrideTitle>
                <!-- Nothing, title is suppressed -->
            </ng-template>
        </jhi-assessment-instructions>
    `,
})
export class OrionAssessmentInstructionsComponent {
    @Input() readOnly: boolean;
    @Input() programmingParticipation?: ProgrammingExerciseStudentParticipation;
    @Input() exercise: Exercise;
}
