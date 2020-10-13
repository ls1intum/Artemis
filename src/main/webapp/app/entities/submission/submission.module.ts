import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionComponent } from './submission.component';
import { SubmissionDetailComponent } from './submission-detail.component';
import { SubmissionUpdateComponent } from './submission-update.component';
import { SubmissionDeleteDialogComponent } from './submission-delete-dialog.component';
import { submissionRoute } from './submission.route';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(submissionRoute)],
    declarations: [SubmissionComponent, SubmissionDetailComponent, SubmissionUpdateComponent, SubmissionDeleteDialogComponent],
    entryComponents: [SubmissionDeleteDialogComponent],
})
export class ArtemisSubmissionModule {}
