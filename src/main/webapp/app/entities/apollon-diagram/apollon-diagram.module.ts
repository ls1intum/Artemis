import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    ApollonDiagramComponent,
    ApollonDiagramDetailComponent,
    ApollonDiagramUpdateComponent,
    ApollonDiagramDeletePopupComponent,
    ApollonDiagramDeleteDialogComponent,
    apollonDiagramRoute,
    apollonDiagramPopupRoute
} from './';

const ENTITY_STATES = [...apollonDiagramRoute, ...apollonDiagramPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ApollonDiagramComponent,
        ApollonDiagramDetailComponent,
        ApollonDiagramUpdateComponent,
        ApollonDiagramDeleteDialogComponent,
        ApollonDiagramDeletePopupComponent
    ],
    entryComponents: [
        ApollonDiagramComponent,
        ApollonDiagramUpdateComponent,
        ApollonDiagramDeleteDialogComponent,
        ApollonDiagramDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSApollonDiagramModule {}
