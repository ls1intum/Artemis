import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    SubmissionService,
    SubmissionPopupService,
    SubmissionComponent,
    SubmissionDetailComponent,
    SubmissionDialogComponent,
    SubmissionPopupComponent,
    SubmissionDeletePopupComponent,
    SubmissionDeleteDialogComponent,
    submissionRoute,
    submissionPopupRoute,
} from './';

const ENTITY_STATES = [
    ...submissionRoute,
    ...submissionPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        SubmissionComponent,
        SubmissionDetailComponent,
        SubmissionDialogComponent,
        SubmissionDeleteDialogComponent,
        SubmissionPopupComponent,
        SubmissionDeletePopupComponent,
    ],
    entryComponents: [
        SubmissionComponent,
        SubmissionDialogComponent,
        SubmissionPopupComponent,
        SubmissionDeleteDialogComponent,
        SubmissionDeletePopupComponent,
    ],
    providers: [
        SubmissionService,
        SubmissionPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSSubmissionModule {}
