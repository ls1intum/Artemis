import { TransformationModelingSubmissionComponent } from './transformation-modeling-submission.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { MomentModule } from 'ngx-moment';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ArtemisTransformationModelingParticipationRoutingModule } from 'app/exercises/transformation/participate/transformation-modeling-participation.route';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisTeamSubmissionSyncModule } from 'app/exercises/shared/team-submission-sync/team-submission-sync.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisModelingEditorModule,
        ArtemisComplaintsModule,
        ArtemisSharedComponentModule,
        ArtemisTransformationModelingParticipationRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ModelingAssessmentModule,
        MomentModule,
        ArtemisTeamModule,
        ArtemisTeamSubmissionSyncModule,
        ArtemisFullscreenModule,
        RatingModule,
        ArtemisMarkdownModule,
    ],
    declarations: [TransformationModelingSubmissionComponent],
})
export class ArtemisTransformationModelingParticipationModule {}
