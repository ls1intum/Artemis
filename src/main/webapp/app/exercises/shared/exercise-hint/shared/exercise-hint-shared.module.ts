import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CastToCodeHintPipe } from 'app/exercises/shared/exercise-hint/services/code-hint-cast.pipe';
import { SolutionEntryComponent } from 'app/exercises/shared/exercise-hint/shared/solution-entry.component';
import { CodeHintContainerComponent } from 'app/exercises/shared/exercise-hint/shared/code-hint-container.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownModule, MonacoEditorModule],
    declarations: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe],
    exports: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe],
})
export class ArtemisExerciseHintSharedModule {}
