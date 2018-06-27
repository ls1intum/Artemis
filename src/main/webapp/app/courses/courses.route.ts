import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../shared';
import { CoursesComponent } from './courses.component';

export const coursesRoute: Routes = [
    {
        path: 'courses',
        component: CoursesComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'courses/:courseId/exercise/:exerciseId',
        component: CoursesComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.course.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
