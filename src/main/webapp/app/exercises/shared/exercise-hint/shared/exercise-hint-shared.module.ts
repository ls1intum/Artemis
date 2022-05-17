import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CastToCodeHintPipe } from 'app/exercises/shared/exercise-hint/shared/code-hint-cast.pipe';
import { SolutionEntryComponent } from 'app/exercises/shared/exercise-hint/shared/solution-entry.component';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { CodeHintContainerComponent } from 'app/exercises/shared/exercise-hint/shared/code-hint-container.component';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule],
    declarations: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe],
    exports: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe],
})
export class ArtemisExerciseHintSharedModule {}
