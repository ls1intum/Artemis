import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    ResultComponent,
    ResultDetailComponent,
    ResultUpdateComponent,
    ResultDeletePopupComponent,
    ResultDeleteDialogComponent,
    resultRoute,
    resultPopupRoute
} from './';

const ENTITY_STATES = [...resultRoute, ...resultPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [ResultComponent, ResultDetailComponent, ResultUpdateComponent, ResultDeleteDialogComponent, ResultDeletePopupComponent],
    entryComponents: [ResultComponent, ResultUpdateComponent, ResultDeleteDialogComponent, ResultDeletePopupComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSResultModule {}
