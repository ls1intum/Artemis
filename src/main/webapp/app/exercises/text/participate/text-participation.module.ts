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
import { ArtemisTeamSubmissionSyncModule } from 'app/exercises/shared/team-submission-sync/team-submission-sync.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisTeamParticipeModule } from 'app/exercises/shared/team/team-participate/team-participate.module';
import { IrisModule } from 'app/iris/iris.module';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/exercise-chatbot/exercise-chatbot-button.component';

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
        IrisModule,
        IrisExerciseChatbotButtonComponent,
    ],
    declarations: [TextEditorComponent, TextResultComponent],
    exports: [TextEditorComponent],
})
export class ArtemisTextParticipationModule {}
