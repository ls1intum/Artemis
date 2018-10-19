import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    AnswerCounterComponent,
    AnswerCounterDetailComponent,
    AnswerCounterUpdateComponent,
    AnswerCounterDeletePopupComponent,
    AnswerCounterDeleteDialogComponent,
    answerCounterRoute,
    answerCounterPopupRoute
} from './';

const ENTITY_STATES = [...answerCounterRoute, ...answerCounterPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        AnswerCounterComponent,
        AnswerCounterDetailComponent,
        AnswerCounterUpdateComponent,
        AnswerCounterDeleteDialogComponent,
        AnswerCounterDeletePopupComponent
    ],
    entryComponents: [
        AnswerCounterComponent,
        AnswerCounterUpdateComponent,
        AnswerCounterDeleteDialogComponent,
        AnswerCounterDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSAnswerCounterModule {}
