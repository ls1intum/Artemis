import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { ProgrammingExerciseInstructionComponent } from 'app/entities/programming-exercise/instructions/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/entities/programming-exercise/instructions/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/entities/programming-exercise/instructions/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExercisePlantUmlService } from 'app/entities/programming-exercise/instructions/instructions-render/service/programming-exercise-plant-uml.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/entities/programming-exercise/instructions/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/entities/programming-exercise/instructions/instructions-render/task/programming-exercise-instruction-task-status.component';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownEditorModule, ArtemisResultModule],
    declarations: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionStepWizardComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    entryComponents: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    exports: [ProgrammingExerciseInstructionComponent],
    providers: [ProgrammingExerciseTaskExtensionWrapper, ProgrammingExercisePlantUmlExtensionWrapper, ProgrammingExerciseInstructionService, ProgrammingExercisePlantUmlService],
})
export class ArtemisProgrammingExerciseInstructionsRenderModule {}
