import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { CourseScoresComponent } from './course-scores.component';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisCourseScoresRoutingModule } from 'app/course/course-scores/course-scores-routing.module';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisCourseScoresRoutingModule, SortByModule],
    declarations: [CourseScoresComponent],
})
export class ArtemisCourseScoresModule {}
