import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    SubmittedAnswerComponent,
    SubmittedAnswerDetailComponent,
    SubmittedAnswerUpdateComponent,
    SubmittedAnswerDeletePopupComponent,
    SubmittedAnswerDeleteDialogComponent,
    submittedAnswerRoute,
    submittedAnswerPopupRoute
} from './';

const ENTITY_STATES = [...submittedAnswerRoute, ...submittedAnswerPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        SubmittedAnswerComponent,
        SubmittedAnswerDetailComponent,
        SubmittedAnswerUpdateComponent,
        SubmittedAnswerDeleteDialogComponent,
        SubmittedAnswerDeletePopupComponent
    ],
    entryComponents: [
        SubmittedAnswerComponent,
        SubmittedAnswerUpdateComponent,
        SubmittedAnswerDeleteDialogComponent,
        SubmittedAnswerDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSSubmittedAnswerModule {}
