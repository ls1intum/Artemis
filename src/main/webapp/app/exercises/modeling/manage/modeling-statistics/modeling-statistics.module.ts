import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ModelingStatisticsComponent } from './modeling-statistics.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisModelingStatisticsRoutingModule } from 'app/exercises/modeling/manage/modeling-statistics/modeling-statistics.route';

@NgModule({
    imports: [ArtemisSharedModule, NgbModule, ArtemisModelingStatisticsRoutingModule],
    declarations: [ModelingStatisticsComponent],
})
export class ArtemisModelingStatisticsModule {}
