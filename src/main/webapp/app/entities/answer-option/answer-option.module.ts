import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    AnswerOptionService,
    AnswerOptionPopupService,
    AnswerOptionComponent,
    AnswerOptionDetailComponent,
    AnswerOptionDialogComponent,
    AnswerOptionPopupComponent,
    AnswerOptionDeletePopupComponent,
    AnswerOptionDeleteDialogComponent,
    answerOptionRoute,
    answerOptionPopupRoute,
} from './';

const ENTITY_STATES = [
    ...answerOptionRoute,
    ...answerOptionPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        AnswerOptionComponent,
        AnswerOptionDetailComponent,
        AnswerOptionDialogComponent,
        AnswerOptionDeleteDialogComponent,
        AnswerOptionPopupComponent,
        AnswerOptionDeletePopupComponent,
    ],
    entryComponents: [
        AnswerOptionComponent,
        AnswerOptionDialogComponent,
        AnswerOptionPopupComponent,
        AnswerOptionDeleteDialogComponent,
        AnswerOptionDeletePopupComponent,
    ],
    providers: [
        AnswerOptionService,
        AnswerOptionPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSAnswerOptionModule {}
