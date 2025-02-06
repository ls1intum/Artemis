import { NgModule } from '@angular/core';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisProgrammingParticipationRoutingModule } from 'app/exercises/programming/participate/programming-participation-routing.module';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/participate/code-editor-student-container.component';

import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

import { ArtemisProgrammingSubmissionPolicyStatusModule } from 'app/exercises/programming/participate/programming-submission-policy-status.module';

@NgModule({
    imports: [
        ArtemisProgrammingParticipationRoutingModule,
        ArtemisCodeEditorModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisResultModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        SubmissionResultStatusModule,

        ArtemisProgrammingSubmissionPolicyStatusModule,
        CodeEditorStudentContainerComponent,
    ],
})
export class ArtemisProgrammingParticipationModule {}
