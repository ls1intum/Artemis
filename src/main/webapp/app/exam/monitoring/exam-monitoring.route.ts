import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamMonitoringComponent } from 'app/exam/monitoring/exam-monitoring.component';
import { MonitoringOverviewComponent } from 'app/exam/monitoring/subpages/overview/monitoring-overview.component';
import { MonitoringExercisesComponent } from 'app/exam/monitoring/subpages/exercise/monitoring-exercises.component';
import { MonitoringActivityLogComponent } from 'app/exam/monitoring/subpages/activity-log/monitoring-activity-log.component';
import { MonitoringStudentsComponent } from 'app/exam/monitoring/subpages/students/monitoring-students.component';

export const examMonitoringRoute: Routes = [
    {
        path: '',
        redirectTo: 'overview',
        pathMatch: 'full',
    },
    {
        path: '',
        component: ExamMonitoringComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examMonitoring.title',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'overview',
                component: MonitoringOverviewComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.examMonitoring.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'exercises',
                component: MonitoringExercisesComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.examMonitoring.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'students',
                component: MonitoringStudentsComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.examMonitoring.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'activity-log',
                component: MonitoringActivityLogComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.examMonitoring.title',
                },
                canActivate: [UserRouteAccessService],
            },
        ],
    },
];

const EXAM_MONITORING_ROUTES = [...examMonitoringRoute];

export const examMonitoringState: Routes = [
    {
        path: '',
        children: EXAM_MONITORING_ROUTES,
    },
];
