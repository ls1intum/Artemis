import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/manage/instructions/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/exercises/programming/manage/instructions/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/exercises/programming/manage/instructions/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExercisePlantUmlService } from 'app/exercises/programming/manage/instructions/instructions-render/service/programming-exercise-plant-uml.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/manage/instructions/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/manage/instructions/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/manage/instructions/instructions-render/task/programming-exercise-instruction-task-status.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownEditorModule, ArtemisResultModule],
    declarations: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionStepWizardComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    entryComponents: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    exports: [ProgrammingExerciseInstructionComponent],
    providers: [ProgrammingExerciseTaskExtensionWrapper, ProgrammingExercisePlantUmlExtensionWrapper, ProgrammingExerciseInstructionService, ProgrammingExercisePlantUmlService],
})
export class ArtemisProgrammingExerciseInstructionsRenderModule {}
