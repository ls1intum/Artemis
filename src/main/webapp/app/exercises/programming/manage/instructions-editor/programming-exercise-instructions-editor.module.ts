import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseInstructionsRenderModule, ArtemisMarkdownEditorModule, ArtemisProgrammingExerciseStatusModule],
    declarations: [ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionAnalysisComponent],
    providers: [ProgrammingExerciseInstructionAnalysisService],
    exports: [ArtemisProgrammingExerciseInstructionsRenderModule, ProgrammingExerciseEditableInstructionComponent],
})
export class ArtemisProgrammingExerciseInstructionsEditorModule {}
