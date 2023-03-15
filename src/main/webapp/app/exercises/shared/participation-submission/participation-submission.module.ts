import { NgModule } from '@angular/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';

import { ArtemisParticipationSubmissionRoutingModule } from 'app/exercises/shared/participation-submission/participation-submission-routing.module';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisParticipationSubmissionRoutingModule, NgxDatatableModule, ArtemisResultModule, SubmissionResultStatusModule],
    declarations: [ParticipationSubmissionComponent],
})
export class ArtemisParticipationSubmissionModule {}
