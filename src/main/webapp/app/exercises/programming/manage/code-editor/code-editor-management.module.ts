import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisCodeEditorManagementRoutingModule } from 'app/exercises/programming/manage/code-editor/code-editor-management-routing.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { CodeEditorInstructorContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-container.component';
import { CodeEditorInstructorOrionContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-orion-container.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisExerciseHintModule } from 'app/exercises/shared/exercise-hint/exercise-hint.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisCodeEditorManagementRoutingModule,
        ArtemisCodeEditorModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisExerciseHintModule,
        OrionModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
    ],
    declarations: [CodeEditorInstructorContainerComponent, CodeEditorInstructorOrionContainerComponent],
})
export class ArtemisCodeEditorManagementModule {}
