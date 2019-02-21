import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSAdminModule } from 'app/admin/admin.module';
import {
    ParticipationCleanupBuildPlanDialogComponent,
    ParticipationCleanupBuildPlanPopupComponent,
    ParticipationComponent,
    ParticipationDeleteDialogComponent,
    ParticipationDeletePopupComponent,
    ParticipationDetailComponent,
    participationPopupRoute,
    ParticipationPopupService,
    participationRoute,
    ParticipationService
} from './';
import { SortByModule } from 'app/components/pipes';

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
        ParticipationCleanupBuildPlanDialogComponent,
        ParticipationCleanupBuildPlanPopupComponent
    ],
    entryComponents: [
        ParticipationComponent,
        ParticipationDeleteDialogComponent,
        ParticipationDeletePopupComponent,
        ParticipationCleanupBuildPlanDialogComponent,
        ParticipationCleanupBuildPlanPopupComponent

    ],
    providers: [
        ParticipationService,
        ParticipationPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSParticipationModule {}
