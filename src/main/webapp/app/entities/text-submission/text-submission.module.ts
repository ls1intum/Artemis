import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    TextSubmissionComponent,
    TextSubmissionDetailComponent,
    TextSubmissionUpdateComponent,
    TextSubmissionDeletePopupComponent,
    TextSubmissionDeleteDialogComponent,
    textSubmissionRoute,
    textSubmissionPopupRoute
} from './';

const ENTITY_STATES = [...textSubmissionRoute, ...textSubmissionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        TextSubmissionComponent,
        TextSubmissionDetailComponent,
        TextSubmissionUpdateComponent,
        TextSubmissionDeleteDialogComponent,
        TextSubmissionDeletePopupComponent
    ],
    entryComponents: [
        TextSubmissionComponent,
        TextSubmissionUpdateComponent,
        TextSubmissionDeleteDialogComponent,
        TextSubmissionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSTextSubmissionModule {}
