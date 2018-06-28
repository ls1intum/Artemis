import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import { ArTeMiSAdminModule } from '../../admin/admin.module';
import {
    LtiUserIdService,
    LtiUserIdPopupService,
    LtiUserIdComponent,
    LtiUserIdDetailComponent,
    LtiUserIdDialogComponent,
    LtiUserIdPopupComponent,
    LtiUserIdDeletePopupComponent,
    LtiUserIdDeleteDialogComponent,
    ltiUserIdRoute,
    ltiUserIdPopupRoute,
} from './';

const ENTITY_STATES = [
    ...ltiUserIdRoute,
    ...ltiUserIdPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        ArTeMiSAdminModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        LtiUserIdComponent,
        LtiUserIdDetailComponent,
        LtiUserIdDialogComponent,
        LtiUserIdDeleteDialogComponent,
        LtiUserIdPopupComponent,
        LtiUserIdDeletePopupComponent,
    ],
    entryComponents: [
        LtiUserIdComponent,
        LtiUserIdDialogComponent,
        LtiUserIdPopupComponent,
        LtiUserIdDeleteDialogComponent,
        LtiUserIdDeletePopupComponent,
    ],
    providers: [
        LtiUserIdService,
        LtiUserIdPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSLtiUserIdModule {}
