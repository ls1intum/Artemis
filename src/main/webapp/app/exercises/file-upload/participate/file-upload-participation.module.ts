import { NgModule } from '@angular/core';

import { ArtemisFileUploadParticipationRoutingModule } from './file-upload-participation.route';

import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';

@NgModule({
    imports: [ArtemisResultModule, ArtemisComplaintsModule, ArtemisFileUploadParticipationRoutingModule, ArtemisHeaderExercisePageWithDetailsModule, FileUploadSubmissionComponent],
    exports: [FileUploadSubmissionComponent],
})
export class ArtemisFileUploadParticipationModule {}
