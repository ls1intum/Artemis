import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    CourseComponent,
    CourseDeleteDialogComponent,
    CourseDeletePopupComponent,
    CourseDetailComponent,
    CourseDialogComponent,
    CourseParticipationService,
    CoursePopupComponent,
    coursePopupRoute,
    CoursePopupService,
    courseRoute,
    CourseService
} from './';
import { CourseResultService, CourseScoresService } from './course.service';

const ENTITY_STATES = [
    ...courseRoute,
    ...coursePopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
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
        CourseParticipationService,
        CourseResultService,
        CourseScoresService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseModule {}
