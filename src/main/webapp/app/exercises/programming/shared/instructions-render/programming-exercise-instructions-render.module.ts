import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ExamExerciseUpdateHighlighterModule } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisResultModule, ArtemisMarkdownModule, ExamExerciseUpdateHighlighterModule],
    declarations: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionStepWizardComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    exports: [ProgrammingExerciseInstructionComponent],
})
export class ArtemisProgrammingExerciseInstructionsRenderModule {}
