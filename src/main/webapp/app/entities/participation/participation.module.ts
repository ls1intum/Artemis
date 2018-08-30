import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import { ArTEMiSAdminModule } from '../../admin/admin.module';
import { ParticipationComponent, ParticipationDeleteDialogComponent, ParticipationDeletePopupComponent, ParticipationDetailComponent, participationPopupRoute, ParticipationPopupService, participationRoute, ParticipationService } from './';
import { SortByModule } from '../../components/pipes';

const ENTITY_STATES = [
    ...participationRoute,
    ...participationPopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        ArTEMiSAdminModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule
    ],
    declarations: [
        ParticipationComponent,
        ParticipationDetailComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
    ],
    entryComponents: [
        ParticipationComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
    ],
    providers: [
        ParticipationService,
        ParticipationPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSParticipationModule {}
