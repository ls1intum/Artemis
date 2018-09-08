import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DragAndDropSubmittedAnswerComponent,
    DragAndDropSubmittedAnswerDetailComponent,
    DragAndDropSubmittedAnswerUpdateComponent,
    DragAndDropSubmittedAnswerDeletePopupComponent,
    DragAndDropSubmittedAnswerDeleteDialogComponent,
    dragAndDropSubmittedAnswerRoute,
    dragAndDropSubmittedAnswerPopupRoute
} from './';

const ENTITY_STATES = [...dragAndDropSubmittedAnswerRoute, ...dragAndDropSubmittedAnswerPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DragAndDropSubmittedAnswerComponent,
        DragAndDropSubmittedAnswerDetailComponent,
        DragAndDropSubmittedAnswerUpdateComponent,
        DragAndDropSubmittedAnswerDeleteDialogComponent,
        DragAndDropSubmittedAnswerDeletePopupComponent
    ],
    entryComponents: [
        DragAndDropSubmittedAnswerComponent,
        DragAndDropSubmittedAnswerUpdateComponent,
        DragAndDropSubmittedAnswerDeleteDialogComponent,
        DragAndDropSubmittedAnswerDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropSubmittedAnswerModule {}
