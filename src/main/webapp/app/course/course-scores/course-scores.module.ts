import { NgModule } from '@angular/core';
import { ArtemisCourseScoresRoutingModule } from 'app/course/course-scores/course-scores-routing.module';
import { ExportModule } from 'app/shared/export/export.module';
import { ArtemisParticipantScoresModule } from 'app/shared/participant-scores/participant-scores.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseScoresComponent } from './course-scores.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCourseScoresRoutingModule, ArtemisParticipantScoresModule, ExportModule],
    declarations: [CourseScoresComponent],
})
export class ArtemisCourseScoresModule {}
