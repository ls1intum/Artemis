import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { BarChartModule } from '@swimlane/ngx-charts';

import { TutorEffortStatisticsComponent } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component';
import { tutorEffortStatisticsRoute } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.route';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...tutorEffortStatisticsRoute];
@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), BarChartModule, ArtemisSharedComponentModule],
    declarations: [TutorEffortStatisticsComponent],
})
export class ArtemisTutorEffortStatisticsModule {}
