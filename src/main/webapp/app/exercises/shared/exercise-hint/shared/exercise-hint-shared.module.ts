import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CastToCodeHintPipe } from 'app/exercises/shared/exercise-hint/shared/code-hint-cast.pipe';
import { SolutionEntryComponent } from 'app/exercises/shared/exercise-hint/shared/solution-entry.component';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { CodeHintContainerComponent } from 'app/exercises/shared/exercise-hint/shared/code-hint-container.component';
import { MatExpansionModule } from '@angular/material/expansion';
import { ExerciseHintExpandableComponent } from 'app/exercises/shared/exercise-hint/shared/exercise-hint-expandable.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedModule, AceEditorModule, ArtemisMarkdownModule, MatExpansionModule],
    declarations: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe, ExerciseHintExpandableComponent],
    exports: [SolutionEntryComponent, CodeHintContainerComponent, CastToCodeHintPipe, ExerciseHintExpandableComponent],
})
export class ArtemisExerciseHintSharedModule {}
