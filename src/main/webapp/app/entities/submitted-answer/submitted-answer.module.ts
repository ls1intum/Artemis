import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    SubmittedAnswerService,
    SubmittedAnswerPopupService,
    SubmittedAnswerComponent,
    SubmittedAnswerDetailComponent,
    SubmittedAnswerDialogComponent,
    SubmittedAnswerPopupComponent,
    SubmittedAnswerDeletePopupComponent,
    SubmittedAnswerDeleteDialogComponent,
    submittedAnswerRoute,
    submittedAnswerPopupRoute,
} from './';

const ENTITY_STATES = [
    ...submittedAnswerRoute,
    ...submittedAnswerPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        SubmittedAnswerComponent,
        SubmittedAnswerDetailComponent,
        SubmittedAnswerDialogComponent,
        SubmittedAnswerDeleteDialogComponent,
        SubmittedAnswerPopupComponent,
        SubmittedAnswerDeletePopupComponent,
    ],
    entryComponents: [
        SubmittedAnswerComponent,
        SubmittedAnswerDialogComponent,
        SubmittedAnswerPopupComponent,
        SubmittedAnswerDeleteDialogComponent,
        SubmittedAnswerDeletePopupComponent,
    ],
    providers: [
        SubmittedAnswerService,
        SubmittedAnswerPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSSubmittedAnswerModule {}
