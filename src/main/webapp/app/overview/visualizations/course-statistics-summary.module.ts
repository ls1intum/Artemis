import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { BarChartModule, PieChartModule } from '@swimlane/ngx-charts';
import { CourseStatisticsSummaryComponent } from 'app/overview/visualizations/course-statistics-summary/course-statistics-summary.component';

@NgModule({
    imports: [ArtemisSharedModule, FontAwesomeModule, BarChartModule, PieChartModule, ArtemisSharedComponentModule],
    declarations: [CourseStatisticsSummaryComponent],
    exports: [CourseStatisticsSummaryComponent],
})
export class CourseStatisticsSummaryModule {}
