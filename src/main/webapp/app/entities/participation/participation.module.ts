import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import { ArTeMiSAdminModule } from 'app/admin/admin.module';
import {
    ParticipationComponent,
    ParticipationDetailComponent,
    ParticipationUpdateComponent,
    ParticipationDeletePopupComponent,
    ParticipationDeleteDialogComponent,
    participationRoute,
    participationPopupRoute
} from './';

const ENTITY_STATES = [...participationRoute, ...participationPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, ArTeMiSAdminModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ParticipationComponent,
        ParticipationDetailComponent,
        ParticipationUpdateComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent
    ],
    entryComponents: [
        ParticipationComponent,
        ParticipationUpdateComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSParticipationModule {}
