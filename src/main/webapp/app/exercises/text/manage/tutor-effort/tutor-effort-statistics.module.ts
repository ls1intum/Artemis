import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { TutorEffortStatisticsComponent } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component';
import { tutorEffortStatisticsRoute } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.route';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

const ENTITY_STATES = [...tutorEffortStatisticsRoute];
@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), BarChartModule, ArtemisSharedComponentModule, TutorEffortStatisticsComponent],
})
export class ArtemisTutorEffortStatisticsModule {}
