import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { clusterStatisticsRoute } from 'app/exercises/text/manage/cluster-statistics/cluster-statistics.route';
import { ClusterStatisticsComponent } from 'app/exercises/text/manage/cluster-statistics/cluster-statistics.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';

const ENTITY_STATES = [...clusterStatisticsRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisTutorParticipationGraphModule],
    declarations: [ClusterStatisticsComponent],
})
export class ArtemisTextClusterStatisticsModule {}
