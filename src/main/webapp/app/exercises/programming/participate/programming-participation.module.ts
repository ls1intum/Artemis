import { NgModule } from '@angular/core';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisProgrammingParticipationRoutingModule } from 'app/exercises/programming/participate/programming-participation-routing.module';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/participate/code-editor-student-container.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisExerciseHintParticipationModule } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-participation.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisProgrammingParticipationRoutingModule,
        ArtemisCodeEditorModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        SubmissionResultStatusModule,
        ArtemisExerciseHintParticipationModule,
    ],
    declarations: [CodeEditorStudentContainerComponent],
})
export class ArtemisProgrammingParticipationModule {}
