import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    CourseComponent,
    CourseDeleteDialogComponent,
    CourseDeletePopupComponent,
    CourseDetailComponent,
    CourseDialogComponent,
    CourseScoreCalculationComponent,
    CourseParticipationService,
    CoursePopupComponent,
    coursePopupRoute,
    CoursePopupService,
    courseRoute,
    CourseService
} from './';
import { CourseResultService, CourseScoresService } from './course.service';
import  { CourseScoreCalculationService } from './courseScoreCalculation.service';

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
        CourseScoreCalculationComponent
    ],
    entryComponents: [
        CourseComponent,
        CourseDialogComponent,
        CoursePopupComponent,
        CourseDeleteDialogComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent
    ],
    providers: [
        CourseService,
        CoursePopupService,
        CourseParticipationService,
        CourseResultService,
        CourseScoresService,
        CourseScoreCalculationService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseModule {}
