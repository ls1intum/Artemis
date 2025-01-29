import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { TaskCountWarningComponent } from './analysis/task-count-warning/task-count-warning.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisMarkdownEditorModule,
        ArtemisProgrammingExerciseStatusModule,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructionAnalysisComponent,
        TaskCountWarningComponent,
    ],
    providers: [ProgrammingExerciseInstructionAnalysisService],
    exports: [ProgrammingExerciseEditableInstructionComponent],
})
export class ArtemisProgrammingExerciseInstructionsEditorModule {}
