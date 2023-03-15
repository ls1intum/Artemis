import { NgModule } from '@angular/core';

import { CastToCodeHintPipe } from 'app/exercises/shared/exercise-hint/services/code-hint-cast.pipe';
import { CodeHintContainerComponent } from 'app/exercises/shared/exercise-hint/shared/code-hint-container.component';
import { SolutionEntryComponent } from 'app/exercises/shared/exercise-hint/shared/solution-entry.component';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule, ArtemisMarkdownModule],
    declarations: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe],
    exports: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe],
})
export class ArtemisExerciseHintSharedModule {}
