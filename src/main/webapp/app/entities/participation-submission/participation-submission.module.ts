import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { SortByModule } from 'app/components/pipes';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import {
    ParticipationSubmissionComponent,
    ParticipationSubmissionDeleteDialogComponent,
    ParticipationSubmissionDeletePopupComponent,
    participationSubmissionPopupRoute,
    ParticipationSubmissionPopupService,
    participationSubmissionRoute,
} from './';
import { ArtemisResultModule } from 'app/entities/result';

const ENTITY_STATES = [...participationSubmissionRoute, ...participationSubmissionPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, NgxDatatableModule, ArtemisResultModule],

    declarations: [ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent, ParticipationSubmissionComponent],
    entryComponents: [ParticipationSubmissionComponent, ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent],
    providers: [SubmissionService, ParticipationSubmissionPopupService],
})
export class ArtemisParticipationSubmissionModule {}
