import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CourseDashboardComponent } from 'app/overview/course-dashboard/course-dashboard.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisExerciseScoresChartModule } from 'app/overview/visualizations/exercise-scores-chart.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { CourseStatisticsSummaryModule } from 'app/overview/visualizations/course-statistics-summary.module';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { ProactiveWelcomeBannerComponent } from 'app/overview/course-dashboard/proactive-welcome-banner/proactive-welcome-banner.component';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { IrisModule } from 'app/iris/iris.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: CourseDashboardComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.dashboard',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    declarations: [CourseDashboardComponent, ProactiveWelcomeBannerComponent],
    exports: [CourseDashboardComponent],
    imports: [
        CommonModule,
        RouterModule.forChild(routes),
        ArtemisCompetenciesModule,
        ArtemisSharedComponentModule,
        ArtemisExerciseScoresChartModule,
        ArtemisSharedModule,
        FontAwesomeModule,
        CourseStatisticsSummaryModule,
        ArtemisCourseExerciseRowModule,
        OrionModule,
        IrisModule,
    ],
})
export class CourseDashboardModule {}
