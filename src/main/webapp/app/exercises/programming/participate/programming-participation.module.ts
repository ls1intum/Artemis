import { NgModule } from '@angular/core';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisProgrammingParticipationRoutingModule } from 'app/exercises/programming/participate/programming-participation-routing.module';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/participate/code-editor-student-container.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisExerciseHintParticipationModule } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-participation.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisProgrammingParticipationRoutingModule,
        ArtemisCodeEditorModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisExerciseHintParticipationModule,
        ArtemisResultModule,
        ArtemisProgrammingAssessmentModule,
    ],
    declarations: [CodeEditorStudentContainerComponent, CodeEditorTutorAssessmentContainerComponent],
})
export class ArtemisProgrammingParticipationModule {}
