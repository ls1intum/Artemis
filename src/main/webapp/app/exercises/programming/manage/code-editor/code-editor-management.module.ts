import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisCodeEditorManagementRoutingModule } from 'app/exercises/programming/manage/code-editor/code-editor-management-routing.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-and-editor-container.component';
import { CodeEditorInstructorAndEditorOrionContainerComponent } from 'app/orion/management/code-editor-instructor-and-editor-orion-container.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisExerciseHintParticipationModule } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-participation.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisCodeEditorManagementRoutingModule,
        ArtemisCodeEditorModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisExerciseHintParticipationModule,
        OrionModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisHeaderExercisePageWithDetailsModule,
    ],
    declarations: [CodeEditorInstructorAndEditorContainerComponent, CodeEditorInstructorAndEditorOrionContainerComponent],
})
export class ArtemisCodeEditorManagementModule {}
