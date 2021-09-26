import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { ArtemisParticipationSubmissionRoutingModule } from 'app/exercises/shared/participation-submission/participation-submission-routing.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisParticipationSubmissionRoutingModule, NgxDatatableModule, ArtemisResultModule],
    declarations: [ParticipationSubmissionComponent],
})
export class ArtemisParticipationSubmissionModule {}
