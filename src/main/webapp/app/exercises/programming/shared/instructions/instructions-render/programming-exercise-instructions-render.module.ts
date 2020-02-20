import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions/instructions-render/task/programming-exercise-instruction-task-status.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownEditorModule, ArtemisResultModule],
    declarations: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionStepWizardComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    exports: [ProgrammingExerciseInstructionComponent],
})
export class ArtemisProgrammingExerciseInstructionsRenderModule {}
