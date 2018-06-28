import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    ResultService,
    ResultPopupService,
    ResultComponent,
    ResultDetailComponent,
    ResultDialogComponent,
    ResultPopupComponent,
    ResultDeletePopupComponent,
    ResultDeleteDialogComponent,
    resultRoute,
    resultPopupRoute,
} from './';

const ENTITY_STATES = [
    ...resultRoute,
    ...resultPopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        ResultComponent,
        ResultDetailComponent,
        ResultDialogComponent,
        ResultDeleteDialogComponent,
        ResultPopupComponent,
        ResultDeletePopupComponent,
    ],
    entryComponents: [
        ResultComponent,
        ResultDialogComponent,
        ResultPopupComponent,
        ResultDeleteDialogComponent,
        ResultDeletePopupComponent,
    ],
    providers: [
        ResultService,
        ResultPopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSResultModule {}
