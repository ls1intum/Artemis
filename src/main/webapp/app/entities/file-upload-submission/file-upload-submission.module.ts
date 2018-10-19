import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    FileUploadSubmissionComponent,
    FileUploadSubmissionDetailComponent,
    FileUploadSubmissionUpdateComponent,
    FileUploadSubmissionDeletePopupComponent,
    FileUploadSubmissionDeleteDialogComponent,
    fileUploadSubmissionRoute,
    fileUploadSubmissionPopupRoute
} from './';

const ENTITY_STATES = [...fileUploadSubmissionRoute, ...fileUploadSubmissionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        FileUploadSubmissionComponent,
        FileUploadSubmissionDetailComponent,
        FileUploadSubmissionUpdateComponent,
        FileUploadSubmissionDeleteDialogComponent,
        FileUploadSubmissionDeletePopupComponent
    ],
    entryComponents: [
        FileUploadSubmissionComponent,
        FileUploadSubmissionUpdateComponent,
        FileUploadSubmissionDeleteDialogComponent,
        FileUploadSubmissionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSFileUploadSubmissionModule {}
