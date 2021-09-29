import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisFileUploadParticipationRoutingModule } from './file-upload-participation.route';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisResultModule,
        ArtemisComplaintsModule,
        ArtemisFileUploadParticipationRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        RatingModule,
        ArtemisMarkdownModule,
    ],
    declarations: [FileUploadSubmissionComponent],
})
export class ArtemisFileUploadParticipationModule {}
