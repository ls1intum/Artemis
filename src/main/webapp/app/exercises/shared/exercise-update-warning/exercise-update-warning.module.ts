import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';

@NgModule({
    exports: [ExerciseUpdateWarningComponent],
    imports: [CommonModule, ArtemisSharedModule, ArtemisMarkdownEditorModule, ExerciseUpdateWarningComponent],
})
export class ArtemisExerciseUpdateWarningModule {}
