import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import { ArTeMiSAdminModule } from '../../admin/admin.module';
import {
    ParticipationService,
    ParticipationPopupService,
    ParticipationComponent,
    ParticipationDetailComponent,
    ParticipationDialogComponent,
    ParticipationPopupComponent,
    ParticipationDeletePopupComponent,
    ParticipationDeleteDialogComponent,
    participationRoute,
    participationPopupRoute,
} from './';

const ENTITY_STATES = [
    ...participationRoute,
    ...participationPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        ArTeMiSAdminModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        ParticipationComponent,
        ParticipationDetailComponent,
        ParticipationDialogComponent,
        ParticipationDeleteDialogComponent,
        ParticipationPopupComponent,
        ParticipationDeletePopupComponent,
    ],
    entryComponents: [
        ParticipationComponent,
        ParticipationDialogComponent,
        ParticipationPopupComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
    ],
    providers: [
        ParticipationService,
        ParticipationPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSParticipationModule {}
