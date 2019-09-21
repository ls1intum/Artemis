import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { fileUploadSubmissionRoute } from './file-upload-submission.route';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { ArtemisComplaintsModule } from 'app/complaints';
import { MomentModule } from 'ngx-moment';

const ENTITY_STATES = [...fileUploadSubmissionRoute];

@NgModule({
    declarations: [FileUploadSubmissionComponent],
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisResultModule, ArtemisComplaintsModule, MomentModule],
    providers: [],
})
export class ArtemisFileUploadSubmissionModule {}
