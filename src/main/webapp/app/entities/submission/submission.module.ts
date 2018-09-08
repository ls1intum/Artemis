import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    SubmissionComponent,
    SubmissionDetailComponent,
    SubmissionUpdateComponent,
    SubmissionDeletePopupComponent,
    SubmissionDeleteDialogComponent,
    submissionRoute,
    submissionPopupRoute
} from './';

const ENTITY_STATES = [...submissionRoute, ...submissionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        SubmissionComponent,
        SubmissionDetailComponent,
        SubmissionUpdateComponent,
        SubmissionDeleteDialogComponent,
        SubmissionDeletePopupComponent
    ],
    entryComponents: [SubmissionComponent, SubmissionUpdateComponent, SubmissionDeleteDialogComponent, SubmissionDeletePopupComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSSubmissionModule {}
