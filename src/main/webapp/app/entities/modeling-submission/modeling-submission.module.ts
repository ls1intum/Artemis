import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    ModelingSubmissionService,
    ModelingSubmissionPopupService,
    ModelingSubmissionComponent,
    ModelingSubmissionDetailComponent,
    ModelingSubmissionDialogComponent,
    ModelingSubmissionPopupComponent,
    ModelingSubmissionDeletePopupComponent,
    ModelingSubmissionDeleteDialogComponent,
    modelingSubmissionRoute,
    modelingSubmissionPopupRoute,
} from './';

const ENTITY_STATES = [
    ...modelingSubmissionRoute,
    ...modelingSubmissionPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        ModelingSubmissionComponent,
        ModelingSubmissionDetailComponent,
        ModelingSubmissionDialogComponent,
        ModelingSubmissionDeleteDialogComponent,
        ModelingSubmissionPopupComponent,
        ModelingSubmissionDeletePopupComponent,
    ],
    entryComponents: [
        ModelingSubmissionComponent,
        ModelingSubmissionDialogComponent,
        ModelingSubmissionPopupComponent,
        ModelingSubmissionDeleteDialogComponent,
        ModelingSubmissionDeletePopupComponent,
    ],
    providers: [
        ModelingSubmissionService,
        ModelingSubmissionPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSModelingSubmissionModule {}
