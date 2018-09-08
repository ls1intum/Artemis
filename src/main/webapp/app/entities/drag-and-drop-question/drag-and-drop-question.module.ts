import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DragAndDropQuestionComponent,
    DragAndDropQuestionDetailComponent,
    DragAndDropQuestionUpdateComponent,
    DragAndDropQuestionDeletePopupComponent,
    DragAndDropQuestionDeleteDialogComponent,
    dragAndDropQuestionRoute,
    dragAndDropQuestionPopupRoute
} from './';

const ENTITY_STATES = [...dragAndDropQuestionRoute, ...dragAndDropQuestionPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DragAndDropQuestionComponent,
        DragAndDropQuestionDetailComponent,
        DragAndDropQuestionUpdateComponent,
        DragAndDropQuestionDeleteDialogComponent,
        DragAndDropQuestionDeletePopupComponent
    ],
    entryComponents: [
        DragAndDropQuestionComponent,
        DragAndDropQuestionUpdateComponent,
        DragAndDropQuestionDeleteDialogComponent,
        DragAndDropQuestionDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropQuestionModule {}
