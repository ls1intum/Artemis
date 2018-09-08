import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import { ArTeMiSAdminModule } from 'app/admin/admin.module';
import {
    LtiUserIdComponent,
    LtiUserIdDetailComponent,
    LtiUserIdUpdateComponent,
    LtiUserIdDeletePopupComponent,
    LtiUserIdDeleteDialogComponent,
    ltiUserIdRoute,
    ltiUserIdPopupRoute
} from './';

const ENTITY_STATES = [...ltiUserIdRoute, ...ltiUserIdPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, ArTeMiSAdminModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        LtiUserIdComponent,
        LtiUserIdDetailComponent,
        LtiUserIdUpdateComponent,
        LtiUserIdDeleteDialogComponent,
        LtiUserIdDeletePopupComponent
    ],
    entryComponents: [LtiUserIdComponent, LtiUserIdUpdateComponent, LtiUserIdDeleteDialogComponent, LtiUserIdDeletePopupComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSLtiUserIdModule {}
