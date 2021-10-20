import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TutorEffortStatisticsComponent } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component';
import { tutorEffortStatisticsRoute } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.route';

const ENTITY_STATES = [...tutorEffortStatisticsRoute];
@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [TutorEffortStatisticsComponent],
})
export class ArtemisTutorEffortStatisticsModule {}
