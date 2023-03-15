import { NgModule } from '@angular/core';

import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseInstructionsRenderModule, ArtemisMarkdownEditorModule, ArtemisProgrammingExerciseStatusModule],
    declarations: [ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionAnalysisComponent],
    providers: [ProgrammingExerciseInstructionAnalysisService],
    exports: [ArtemisProgrammingExerciseInstructionsRenderModule, ProgrammingExerciseEditableInstructionComponent],
})
export class ArtemisProgrammingExerciseInstructionsEditorModule {}
