import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import { ArTeMiSAdminModule } from '../../admin/admin.module';
import {
    LtiOutcomeUrlService,
    LtiOutcomeUrlPopupService,
    LtiOutcomeUrlComponent,
    LtiOutcomeUrlDetailComponent,
    LtiOutcomeUrlDialogComponent,
    LtiOutcomeUrlPopupComponent,
    LtiOutcomeUrlDeletePopupComponent,
    LtiOutcomeUrlDeleteDialogComponent,
    ltiOutcomeUrlRoute,
    ltiOutcomeUrlPopupRoute,
} from './';

const ENTITY_STATES = [
    ...ltiOutcomeUrlRoute,
    ...ltiOutcomeUrlPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        ArTeMiSAdminModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        LtiOutcomeUrlComponent,
        LtiOutcomeUrlDetailComponent,
        LtiOutcomeUrlDialogComponent,
        LtiOutcomeUrlDeleteDialogComponent,
        LtiOutcomeUrlPopupComponent,
        LtiOutcomeUrlDeletePopupComponent,
    ],
    entryComponents: [
        LtiOutcomeUrlComponent,
        LtiOutcomeUrlDialogComponent,
        LtiOutcomeUrlPopupComponent,
        LtiOutcomeUrlDeleteDialogComponent,
        LtiOutcomeUrlDeletePopupComponent,
    ],
    providers: [
        LtiOutcomeUrlService,
        LtiOutcomeUrlPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSLtiOutcomeUrlModule {}
