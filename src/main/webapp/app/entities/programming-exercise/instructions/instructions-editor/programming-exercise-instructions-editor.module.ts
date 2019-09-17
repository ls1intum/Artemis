import { NgModule } from '@angular/core';
import { ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionAnalysisComponent, ProgrammingExerciseInstructionAnalysisService } from './';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseInstructionsRenderModule, ArtemisMarkdownEditorModule, ArtemisProgrammingExerciseStatusModule],
    declarations: [ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionAnalysisComponent],
    entryComponents: [ProgrammingExerciseEditableInstructionComponent],
    providers: [ProgrammingExerciseInstructionAnalysisService],
    exports: [ArtemisProgrammingExerciseInstructionsRenderModule, ProgrammingExerciseEditableInstructionComponent],
})
export class ArtemisProgrammingExerciseInstructionsEditorModule {}
