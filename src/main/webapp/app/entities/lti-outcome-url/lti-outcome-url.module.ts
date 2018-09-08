import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import { ArTeMiSAdminModule } from 'app/admin/admin.module';
import {
    LtiOutcomeUrlComponent,
    LtiOutcomeUrlDetailComponent,
    LtiOutcomeUrlUpdateComponent,
    LtiOutcomeUrlDeletePopupComponent,
    LtiOutcomeUrlDeleteDialogComponent,
    ltiOutcomeUrlRoute,
    ltiOutcomeUrlPopupRoute
} from './';

const ENTITY_STATES = [...ltiOutcomeUrlRoute, ...ltiOutcomeUrlPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, ArTeMiSAdminModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        LtiOutcomeUrlComponent,
        LtiOutcomeUrlDetailComponent,
        LtiOutcomeUrlUpdateComponent,
        LtiOutcomeUrlDeleteDialogComponent,
        LtiOutcomeUrlDeletePopupComponent
    ],
    entryComponents: [
        LtiOutcomeUrlComponent,
        LtiOutcomeUrlUpdateComponent,
        LtiOutcomeUrlDeleteDialogComponent,
        LtiOutcomeUrlDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSLtiOutcomeUrlModule {}
