import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseScoresComponent } from './course-scores.component';
import { ArtemisCourseScoresRoutingModule } from 'app/course/course-scores/course-scores-routing.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCourseScoresRoutingModule],
    declarations: [CourseScoresComponent],
})
export class ArtemisCourseScoresModule {}
