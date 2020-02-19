import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { fileUploadSubmissionRoute } from './file-upload-submission.route';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { MomentModule } from 'ngx-moment';
import { FileUploadResultComponent } from 'app/exercises/file-upload/participate/file-upload-submission/file-upload-result/file-upload-result.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';

const ENTITY_STATES = [...fileUploadSubmissionRoute];

@NgModule({
    declarations: [FileUploadSubmissionComponent, FileUploadResultComponent],
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisResultModule, ArtemisComplaintsModule, MomentModule, ArtemisSharedComponentModule],
    providers: [],
})
export class ArtemisFileUploadSubmissionModule {}
