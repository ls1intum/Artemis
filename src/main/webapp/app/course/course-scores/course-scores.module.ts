import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseScoresComponent } from './course-scores.component';
import { ArtemisCourseScoresRoutingModule } from 'app/course/course-scores/course-scores-routing.module';
import { ArtemisParticipantScoresModule } from 'app/shared/participant-scores/participant-scores.module';
import { ExportModule } from 'app/shared/export/export.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCourseScoresRoutingModule, ArtemisParticipantScoresModule, ExportModule],
    declarations: [CourseScoresComponent],
})
export class ArtemisCourseScoresModule {}
