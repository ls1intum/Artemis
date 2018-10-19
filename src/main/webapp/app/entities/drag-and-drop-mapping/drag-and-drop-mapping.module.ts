import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DragAndDropMappingComponent,
    DragAndDropMappingDetailComponent,
    DragAndDropMappingUpdateComponent,
    DragAndDropMappingDeletePopupComponent,
    DragAndDropMappingDeleteDialogComponent,
    dragAndDropMappingRoute,
    dragAndDropMappingPopupRoute
} from './';

const ENTITY_STATES = [...dragAndDropMappingRoute, ...dragAndDropMappingPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DragAndDropMappingComponent,
        DragAndDropMappingDetailComponent,
        DragAndDropMappingUpdateComponent,
        DragAndDropMappingDeleteDialogComponent,
        DragAndDropMappingDeletePopupComponent
    ],
    entryComponents: [
        DragAndDropMappingComponent,
        DragAndDropMappingUpdateComponent,
        DragAndDropMappingDeleteDialogComponent,
        DragAndDropMappingDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDragAndDropMappingModule {}
