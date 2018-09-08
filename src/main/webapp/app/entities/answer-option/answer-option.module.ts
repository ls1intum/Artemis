import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    AnswerOptionComponent,
    AnswerOptionDetailComponent,
    AnswerOptionUpdateComponent,
    AnswerOptionDeletePopupComponent,
    AnswerOptionDeleteDialogComponent,
    answerOptionRoute,
    answerOptionPopupRoute
} from './';

const ENTITY_STATES = [...answerOptionRoute, ...answerOptionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        AnswerOptionComponent,
        AnswerOptionDetailComponent,
        AnswerOptionUpdateComponent,
        AnswerOptionDeleteDialogComponent,
        AnswerOptionDeletePopupComponent
    ],
    entryComponents: [
        AnswerOptionComponent,
        AnswerOptionUpdateComponent,
        AnswerOptionDeleteDialogComponent,
        AnswerOptionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSAnswerOptionModule {}
