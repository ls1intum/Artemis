import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { textEditorRoute } from 'app/exercises/text/participate/text-editor.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisTeamSubmissionSyncModule } from 'app/exercises/shared/team-submission-sync/team-submission-sync.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

const ENTITY_STATES = [...textEditorRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisResultModule,
        ArtemisComplaintsModule,
        ArtemisSharedComponentModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisTeamModule,
        ArtemisTeamSubmissionSyncModule,
        RatingModule,
        ArtemisMarkdownModule,
    ],
    declarations: [TextEditorComponent, TextResultComponent],
})
export class ArtemisTextParticipationModule {}
