import { NgModule } from '@angular/core';

import { ModelingSubmissionComponent } from './modeling-submission.component';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ArtemisModelingParticipationRoutingModule } from 'app/exercises/modeling/participate/modeling-participation.route';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { ArtemisTeamSubmissionSyncModule } from 'app/exercises/shared/team-submission-sync/team-submission-sync.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisModelingEditorModule,
        ArtemisComplaintsModule,
        ArtemisSharedComponentModule,
        ArtemisModelingParticipationRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ModelingAssessmentModule,
        ArtemisTeamSubmissionSyncModule,
        ArtemisFullscreenModule,
        RatingModule,
        ArtemisMarkdownModule,
        ArtemisTeamParticipeModule,
    ],
    declarations: [ModelingSubmissionComponent],
})
export class ArtemisModelingParticipationModule {}
