import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    FileUploadExerciseComponent,
    FileUploadExerciseDetailComponent,
    FileUploadExerciseUpdateComponent,
    FileUploadExerciseDeletePopupComponent,
    FileUploadExerciseDeleteDialogComponent,
    fileUploadExerciseRoute,
    fileUploadExercisePopupRoute
} from './';

const ENTITY_STATES = [...fileUploadExerciseRoute, ...fileUploadExercisePopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        FileUploadExerciseComponent,
        FileUploadExerciseDetailComponent,
        FileUploadExerciseUpdateComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExerciseDeletePopupComponent
    ],
    entryComponents: [
        FileUploadExerciseComponent,
        FileUploadExerciseUpdateComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExerciseDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSFileUploadExerciseModule {}
