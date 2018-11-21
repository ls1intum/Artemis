import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    TextExerciseComponent,
    TextExerciseDeleteDialogComponent,
    TextExerciseDeletePopupComponent,
    TextExerciseDetailComponent,
    TextExerciseUpdateComponent,
    TextExerciseDialogComponent,
    TextExercisePopupComponent,
    textExercisePopupRoute,
    TextExercisePopupService,
    textExerciseRoute,
    TextExerciseService
} from './';
import { SortByModule } from '../../components/pipes';

const ENTITY_STATES = [...textExerciseRoute, ...textExercisePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [
        TextExerciseComponent,
        TextExerciseDetailComponent,
        TextExerciseUpdateComponent,
        TextExerciseDialogComponent,
        TextExerciseDeleteDialogComponent,
        TextExercisePopupComponent,
        TextExerciseDeletePopupComponent
    ],
    entryComponents: [
        TextExerciseComponent,
        TextExerciseDialogComponent,
        TextExerciseUpdateComponent,
        TextExercisePopupComponent,
        TextExerciseDeleteDialogComponent,
        TextExerciseDeletePopupComponent
    ],
    providers: [TextExerciseService, TextExercisePopupService],
    exports: [TextExerciseComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSTextExerciseModule {}
