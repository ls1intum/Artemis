import { NgModule } from '@angular/core';

import { CourseScoresComponent } from './course-scores.component';
import { ArtemisCourseScoresRoutingModule } from 'app/course/course-scores/course-scores-routing.module';

@NgModule({
    imports: [ArtemisCourseScoresRoutingModule, CourseScoresComponent],
})
export class ArtemisCourseScoresModule {}
