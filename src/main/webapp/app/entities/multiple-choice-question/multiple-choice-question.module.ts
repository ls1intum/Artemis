import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    MultipleChoiceQuestionComponent,
    MultipleChoiceQuestionDetailComponent,
    MultipleChoiceQuestionUpdateComponent,
    MultipleChoiceQuestionDeletePopupComponent,
    MultipleChoiceQuestionDeleteDialogComponent,
    multipleChoiceQuestionRoute,
    multipleChoiceQuestionPopupRoute
} from './';

const ENTITY_STATES = [...multipleChoiceQuestionRoute, ...multipleChoiceQuestionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        MultipleChoiceQuestionComponent,
        MultipleChoiceQuestionDetailComponent,
        MultipleChoiceQuestionUpdateComponent,
        MultipleChoiceQuestionDeleteDialogComponent,
        MultipleChoiceQuestionDeletePopupComponent
    ],
    entryComponents: [
        MultipleChoiceQuestionComponent,
        MultipleChoiceQuestionUpdateComponent,
        MultipleChoiceQuestionDeleteDialogComponent,
        MultipleChoiceQuestionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSMultipleChoiceQuestionModule {}
