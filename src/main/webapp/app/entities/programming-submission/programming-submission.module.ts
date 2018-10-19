import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    ProgrammingSubmissionComponent,
    ProgrammingSubmissionDetailComponent,
    ProgrammingSubmissionUpdateComponent,
    ProgrammingSubmissionDeletePopupComponent,
    ProgrammingSubmissionDeleteDialogComponent,
    programmingSubmissionRoute,
    programmingSubmissionPopupRoute
} from './';

const ENTITY_STATES = [...programmingSubmissionRoute, ...programmingSubmissionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ProgrammingSubmissionComponent,
        ProgrammingSubmissionDetailComponent,
        ProgrammingSubmissionUpdateComponent,
        ProgrammingSubmissionDeleteDialogComponent,
        ProgrammingSubmissionDeletePopupComponent
    ],
    entryComponents: [
        ProgrammingSubmissionComponent,
        ProgrammingSubmissionUpdateComponent,
        ProgrammingSubmissionDeleteDialogComponent,
        ProgrammingSubmissionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSProgrammingSubmissionModule {}
