import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import {
    ParticipationSubmissionDeleteDialogComponent,
    ParticipationSubmissionDeletePopupComponent,
} from 'app/exercises/shared/participation-submission/participation-submission-delete-dialog.component';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { ArtemisParticipationSubmissionRoutingModule } from 'app/exercises/shared/participation-submission/participation-submission-routing.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisParticipationSubmissionRoutingModule, SortByModule, NgxDatatableModule, ArtemisResultModule],

    declarations: [ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent, ParticipationSubmissionComponent],
    entryComponents: [ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent],
})
export class ArtemisParticipationSubmissionModule {}
