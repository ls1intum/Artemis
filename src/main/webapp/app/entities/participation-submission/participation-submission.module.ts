import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { participationSubmissionPopupRoute, participationSubmissionRoute } from 'app/entities/participation-submission/participation-submission.route';
import { ParticipationSubmissionPopupService } from 'app/entities/participation-submission/participation-submission-popup.service';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import {
    ParticipationSubmissionDeleteDialogComponent,
    ParticipationSubmissionDeletePopupComponent,
} from 'app/entities/participation-submission/participation-submission-delete-dialog.component';
import { ParticipationSubmissionComponent } from 'app/entities/participation-submission/participation-submission.component';

const ENTITY_STATES = [...participationSubmissionRoute, ...participationSubmissionPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, NgxDatatableModule, ArtemisResultModule],

    declarations: [ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent, ParticipationSubmissionComponent],
    entryComponents: [ParticipationSubmissionComponent, ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent],
    providers: [SubmissionService, ParticipationSubmissionPopupService],
})
export class ArtemisParticipationSubmissionModule {}
