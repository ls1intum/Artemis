import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';

import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    declarations: [ExerciseUpdateWarningComponent],
    exports: [ExerciseUpdateWarningComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule],
})
export class ArtemisExerciseUpdateWarningModule {}
