import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { ArtemisTeamSubmissionSyncModule } from 'app/exercises/shared/team-submission-sync/team-submission-sync.module';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';
import { textEditorRoute } from 'app/exercises/text/participate/text-editor.route';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(textEditorRoute),
        ArtemisResultModule,
        ArtemisComplaintsModule,
        ArtemisSharedComponentModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisTeamSubmissionSyncModule,
        RatingModule,
        ArtemisMarkdownModule,
        ArtemisTeamParticipeModule,
    ],
    declarations: [TextEditorComponent, TextResultComponent],
})
export class ArtemisTextParticipationModule {}
