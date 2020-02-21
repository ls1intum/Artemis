import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisFileUploadParticipationRoutingModule } from './file-upload-participation.route';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { FileUploadResultComponent } from 'app/exercises/file-upload/participate/file-upload-result.component';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { MomentModule } from 'ngx-moment';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisResultModule, ArtemisComplaintsModule, ArtemisFileUploadParticipationRoutingModule, MomentModule],
    declarations: [FileUploadSubmissionComponent, FileUploadResultComponent],
})
export class ArtemisFileUploadParticipationModule {}
