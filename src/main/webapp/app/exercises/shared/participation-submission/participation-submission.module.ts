import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { participationSubmissionPopupRoute, participationSubmissionRoute } from 'app/exercises/shared/participation-submission/participation-submission.route';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import {
    ParticipationSubmissionDeleteDialogComponent,
    ParticipationSubmissionDeletePopupComponent,
} from 'app/exercises/shared/participation-submission/participation-submission-delete-dialog.component';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';

const ENTITY_STATES = [...participationSubmissionRoute, ...participationSubmissionPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, NgxDatatableModule, ArtemisResultModule],

    declarations: [ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent, ParticipationSubmissionComponent],
    entryComponents: [ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent],
})
export class ArtemisParticipationSubmissionModule {}
