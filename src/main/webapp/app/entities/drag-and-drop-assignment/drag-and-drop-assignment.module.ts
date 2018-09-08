import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DragAndDropAssignmentComponent,
    DragAndDropAssignmentDetailComponent,
    DragAndDropAssignmentUpdateComponent,
    DragAndDropAssignmentDeletePopupComponent,
    DragAndDropAssignmentDeleteDialogComponent,
    dragAndDropAssignmentRoute,
    dragAndDropAssignmentPopupRoute
} from './';

const ENTITY_STATES = [...dragAndDropAssignmentRoute, ...dragAndDropAssignmentPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DragAndDropAssignmentComponent,
        DragAndDropAssignmentDetailComponent,
        DragAndDropAssignmentUpdateComponent,
        DragAndDropAssignmentDeleteDialogComponent,
        DragAndDropAssignmentDeletePopupComponent
    ],
    entryComponents: [
        DragAndDropAssignmentComponent,
        DragAndDropAssignmentUpdateComponent,
        DragAndDropAssignmentDeleteDialogComponent,
        DragAndDropAssignmentDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropAssignmentModule {}
