import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseScoresComponent } from './course-scores.component';
import { ArtemisCourseScoresRoutingModule } from 'app/course/course-scores/course-scores-routing.module';
import { ArtemisParticipantScoresModule } from 'app/shared/participant-scores/participant-scores.module';
import { ExportModule } from 'app/shared/export/export.module';
import { CourseManagementTabBarModule } from 'app/shared/course-management-tab-bar/course-management-tab-bar.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCourseScoresRoutingModule, ArtemisParticipantScoresModule, ExportModule, CourseManagementTabBarModule],
    declarations: [CourseScoresComponent],
})
export class ArtemisCourseScoresModule {}
