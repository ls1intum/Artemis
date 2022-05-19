import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamMonitoringComponent } from 'app/exam/monitoring/exam-monitoring.component';

export const examMonitoringRoute: Routes = [
    {
        path: '',
        component: ExamMonitoringComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examMonitoring.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

const EXAM_MONITORING_ROUTES = [...examMonitoringRoute];

export const examMonitoringState: Routes = [
    {
        path: '',
        children: EXAM_MONITORING_ROUTES,
    },
];
