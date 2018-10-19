import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    MultipleChoiceQuestionStatisticComponent,
    MultipleChoiceQuestionStatisticDetailComponent,
    MultipleChoiceQuestionStatisticUpdateComponent,
    MultipleChoiceQuestionStatisticDeletePopupComponent,
    MultipleChoiceQuestionStatisticDeleteDialogComponent,
    multipleChoiceQuestionStatisticRoute,
    multipleChoiceQuestionStatisticPopupRoute
} from './';

const ENTITY_STATES = [...multipleChoiceQuestionStatisticRoute, ...multipleChoiceQuestionStatisticPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        MultipleChoiceQuestionStatisticComponent,
        MultipleChoiceQuestionStatisticDetailComponent,
        MultipleChoiceQuestionStatisticUpdateComponent,
        MultipleChoiceQuestionStatisticDeleteDialogComponent,
        MultipleChoiceQuestionStatisticDeletePopupComponent
    ],
    entryComponents: [
        MultipleChoiceQuestionStatisticComponent,
        MultipleChoiceQuestionStatisticUpdateComponent,
        MultipleChoiceQuestionStatisticDeleteDialogComponent,
        MultipleChoiceQuestionStatisticDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSMultipleChoiceQuestionStatisticModule {}
