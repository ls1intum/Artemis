import { Routes } from '@angular/router';

export const examMonitoringRoute: Routes = [];

const EXAM_MONITORING_ROUTES = [...examMonitoringRoute];

export const examMonitoringState: Routes = [
    {
        path: '',
        children: EXAM_MONITORING_ROUTES,
    },
];
