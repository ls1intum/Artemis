import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    DropLocationCounterComponent,
    DropLocationCounterDetailComponent,
    DropLocationCounterUpdateComponent,
    DropLocationCounterDeletePopupComponent,
    DropLocationCounterDeleteDialogComponent,
    dropLocationCounterRoute,
    dropLocationCounterPopupRoute
} from './';

const ENTITY_STATES = [...dropLocationCounterRoute, ...dropLocationCounterPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        DropLocationCounterComponent,
        DropLocationCounterDetailComponent,
        DropLocationCounterUpdateComponent,
        DropLocationCounterDeleteDialogComponent,
        DropLocationCounterDeletePopupComponent
    ],
    entryComponents: [
        DropLocationCounterComponent,
        DropLocationCounterUpdateComponent,
        DropLocationCounterDeleteDialogComponent,
        DropLocationCounterDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSDropLocationCounterModule {}
