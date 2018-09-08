import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    MultipleChoiceSubmittedAnswerComponent,
    MultipleChoiceSubmittedAnswerDetailComponent,
    MultipleChoiceSubmittedAnswerUpdateComponent,
    MultipleChoiceSubmittedAnswerDeletePopupComponent,
    MultipleChoiceSubmittedAnswerDeleteDialogComponent,
    multipleChoiceSubmittedAnswerRoute,
    multipleChoiceSubmittedAnswerPopupRoute
} from './';

const ENTITY_STATES = [...multipleChoiceSubmittedAnswerRoute, ...multipleChoiceSubmittedAnswerPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        MultipleChoiceSubmittedAnswerComponent,
        MultipleChoiceSubmittedAnswerDetailComponent,
        MultipleChoiceSubmittedAnswerUpdateComponent,
        MultipleChoiceSubmittedAnswerDeleteDialogComponent,
        MultipleChoiceSubmittedAnswerDeletePopupComponent
    ],
    entryComponents: [
        MultipleChoiceSubmittedAnswerComponent,
        MultipleChoiceSubmittedAnswerUpdateComponent,
        MultipleChoiceSubmittedAnswerDeleteDialogComponent,
        MultipleChoiceSubmittedAnswerDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSMultipleChoiceSubmittedAnswerModule {}
