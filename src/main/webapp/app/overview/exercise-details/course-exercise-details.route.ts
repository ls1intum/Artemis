import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/orion/participation/orion-course-exercise-details.component').then((m) => m.OrionCourseExerciseDetailsComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.exercise',
        },
        pathMatch: 'full',
        canActivate: [UserRouteAccessService],
    },
];

export { routes };
