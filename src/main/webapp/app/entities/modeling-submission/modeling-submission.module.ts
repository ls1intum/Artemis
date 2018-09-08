import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    ModelingSubmissionComponent,
    ModelingSubmissionDetailComponent,
    ModelingSubmissionUpdateComponent,
    ModelingSubmissionDeletePopupComponent,
    ModelingSubmissionDeleteDialogComponent,
    modelingSubmissionRoute,
    modelingSubmissionPopupRoute
} from './';

const ENTITY_STATES = [...modelingSubmissionRoute, ...modelingSubmissionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ModelingSubmissionComponent,
        ModelingSubmissionDetailComponent,
        ModelingSubmissionUpdateComponent,
        ModelingSubmissionDeleteDialogComponent,
        ModelingSubmissionDeletePopupComponent
    ],
    entryComponents: [
        ModelingSubmissionComponent,
        ModelingSubmissionUpdateComponent,
        ModelingSubmissionDeleteDialogComponent,
        ModelingSubmissionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSModelingSubmissionModule {}
