import { ModelingSubmissionComponent } from './modeling-submission.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisModelingParticipationRoutingModule } from 'app/exercises/modeling/participate/modeling-participation.route';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';

import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisModelingEditorModule,
        ArtemisComplaintsModule,
        ArtemisSharedComponentModule,
        ArtemisModelingParticipationRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisFullscreenModule,
        ArtemisMarkdownModule,
        ArtemisTeamParticipeModule,
        RequestFeedbackButtonComponent,
        ModelingSubmissionComponent,
    ],
    exports: [ModelingSubmissionComponent],
})
export class ArtemisModelingParticipationModule {}
