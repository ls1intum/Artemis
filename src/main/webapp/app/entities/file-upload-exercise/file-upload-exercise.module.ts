import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    FileUploadExerciseService,
    FileUploadExercisePopupService,
    FileUploadExerciseComponent,
    FileUploadExerciseDetailComponent,
    FileUploadExerciseDialogComponent,
    FileUploadExercisePopupComponent,
    FileUploadExerciseDeletePopupComponent,
    FileUploadExerciseDeleteDialogComponent,
    fileUploadExerciseRoute,
    fileUploadExercisePopupRoute,
} from './';

const ENTITY_STATES = [
    ...fileUploadExerciseRoute,
    ...fileUploadExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        FileUploadExerciseComponent,
        FileUploadExerciseDetailComponent,
        FileUploadExerciseDialogComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExercisePopupComponent,
        FileUploadExerciseDeletePopupComponent,
    ],
    entryComponents: [
        FileUploadExerciseComponent,
        FileUploadExerciseDialogComponent,
        FileUploadExercisePopupComponent,
        FileUploadExerciseDeleteDialogComponent,
        FileUploadExerciseDeletePopupComponent,
    ],
    providers: [
        FileUploadExerciseService,
        FileUploadExercisePopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSFileUploadExerciseModule {}
