import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DragAndDropQuestionStatisticComponent,
    DragAndDropQuestionStatisticDetailComponent,
    DragAndDropQuestionStatisticUpdateComponent,
    DragAndDropQuestionStatisticDeletePopupComponent,
    DragAndDropQuestionStatisticDeleteDialogComponent,
    dragAndDropQuestionStatisticRoute,
    dragAndDropQuestionStatisticPopupRoute
} from './';

const ENTITY_STATES = [...dragAndDropQuestionStatisticRoute, ...dragAndDropQuestionStatisticPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DragAndDropQuestionStatisticComponent,
        DragAndDropQuestionStatisticDetailComponent,
        DragAndDropQuestionStatisticUpdateComponent,
        DragAndDropQuestionStatisticDeleteDialogComponent,
        DragAndDropQuestionStatisticDeletePopupComponent
    ],
    entryComponents: [
        DragAndDropQuestionStatisticComponent,
        DragAndDropQuestionStatisticUpdateComponent,
        DragAndDropQuestionStatisticDeleteDialogComponent,
        DragAndDropQuestionStatisticDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropQuestionStatisticModule {}
