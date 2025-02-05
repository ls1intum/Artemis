import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { CourseDashboardComponent } from 'app/overview/course-dashboard/course-dashboard.component';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseExercisePerformanceComponent } from 'app/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';
import { CourseExerciseLatenessComponent } from 'app/overview/course-dashboard/course-exercise-lateness/course-exercise-lateness.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { LineChartModule } from '@swimlane/ngx-charts';
import { IrisModule } from 'app/iris/iris.module';

import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('app/overview/course-dashboard/course-dashboard.component').then((m) => m.CourseDashboardComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.dashboard',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    exports: [CourseDashboardComponent],
    imports: [
        CommonModule,
        RouterModule.forChild(routes),
        NgbModule,
        ArtemisSharedModule,
        FontAwesomeModule,
        NgxDatatableModule,
        ArtemisSharedComponentModule,
        LineChartModule,
        IrisModule,
        ArtemisCompetenciesModule,
        CourseDashboardComponent,
        CourseExercisePerformanceComponent,
        CourseExerciseLatenessComponent,
    ],
})
export class CourseDashboardModule {}
