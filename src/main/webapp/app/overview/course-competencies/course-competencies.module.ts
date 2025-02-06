import { CourseCompetenciesComponent } from 'app/overview/course-competencies/course-competencies.component';

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.competencies',
        },
        loadComponent: () => import('app/overview/course-competencies/course-competencies.component').then((m) => m.CourseCompetenciesComponent),
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisCompetenciesModule, CourseCompetenciesComponent],
    exports: [CourseCompetenciesComponent],
})
export class CourseCompetenciesModule {}
