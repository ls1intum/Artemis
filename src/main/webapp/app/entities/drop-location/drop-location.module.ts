import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DropLocationComponent,
    DropLocationDetailComponent,
    DropLocationUpdateComponent,
    DropLocationDeletePopupComponent,
    DropLocationDeleteDialogComponent,
    dropLocationRoute,
    dropLocationPopupRoute
} from './';

const ENTITY_STATES = [...dropLocationRoute, ...dropLocationPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DropLocationComponent,
        DropLocationDetailComponent,
        DropLocationUpdateComponent,
        DropLocationDeleteDialogComponent,
        DropLocationDeletePopupComponent
    ],
    entryComponents: [
        DropLocationComponent,
        DropLocationUpdateComponent,
        DropLocationDeleteDialogComponent,
        DropLocationDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDropLocationModule {}
