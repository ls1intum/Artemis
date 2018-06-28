import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    CourseService,
    CoursePopupService,
    CourseComponent,
    CourseDetailComponent,
    CourseDialogComponent,
    CoursePopupComponent,
    CourseDeletePopupComponent,
    CourseDeleteDialogComponent,
    courseRoute,
    coursePopupRoute,
} from './';

const ENTITY_STATES = [
    ...courseRoute,
    ...coursePopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        CourseComponent,
        CourseDetailComponent,
        CourseDialogComponent,
        CourseDeleteDialogComponent,
        CoursePopupComponent,
        CourseDeletePopupComponent,
    ],
    entryComponents: [
        CourseComponent,
        CourseDialogComponent,
        CoursePopupComponent,
        CourseDeleteDialogComponent,
        CourseDeletePopupComponent,
    ],
    providers: [
        CourseService,
        CoursePopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSCourseModule {}
