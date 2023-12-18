import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisExerciseUpdateWarningModule } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.module';

import { MathExerciseComposeComponent } from '../compose/math-exercise-compose.component';

@NgModule({
    imports: [ArtemisSharedModule, FormDateTimePickerModule, ArtemisMarkdownEditorModule, ArtemisSharedComponentModule, ArtemisMarkdownModule, ArtemisExerciseUpdateWarningModule],
    declarations: [MathExerciseComposeComponent],
    exports: [MathExerciseComposeComponent],
})
export class ArtemisMathExerciseComposeModule {}
