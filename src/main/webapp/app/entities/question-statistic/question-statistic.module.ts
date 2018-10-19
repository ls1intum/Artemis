import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    QuestionStatisticComponent,
    QuestionStatisticDetailComponent,
    QuestionStatisticUpdateComponent,
    QuestionStatisticDeletePopupComponent,
    QuestionStatisticDeleteDialogComponent,
    questionStatisticRoute,
    questionStatisticPopupRoute
} from './';

const ENTITY_STATES = [...questionStatisticRoute, ...questionStatisticPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        QuestionStatisticComponent,
        QuestionStatisticDetailComponent,
        QuestionStatisticUpdateComponent,
        QuestionStatisticDeleteDialogComponent,
        QuestionStatisticDeletePopupComponent
    ],
    entryComponents: [
        QuestionStatisticComponent,
        QuestionStatisticUpdateComponent,
        QuestionStatisticDeleteDialogComponent,
        QuestionStatisticDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSQuestionStatisticModule {}
