import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ModelingStatisticsComponent } from './modeling-statistics.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { HomeComponent } from 'app/home/home.component';
import { ArtemisModelingStatisticsRoutingModule } from 'app/exercises/modeling/manage/modeling-statistics/modeling-statistics.route';

@NgModule({
    imports: [ArtemisSharedModule, NgbModule, ArtemisModelingStatisticsRoutingModule],
    declarations: [ModelingStatisticsComponent],
    entryComponents: [HomeComponent, ModelingStatisticsComponent, JhiMainComponent],
})
export class ArtemisModelingStatisticsModule {}
