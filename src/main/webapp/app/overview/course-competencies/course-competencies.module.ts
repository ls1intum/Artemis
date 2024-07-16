import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseCompetenciesComponent } from 'app/overview/course-competencies/course-competencies.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { FireworksModule } from 'app/shared/fireworks/fireworks.module';
import { CourseCompetenciesStudentPageComponent } from 'app/course/competencies/pages/course-competencies-student-page/course-competencies-student-page.component';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.competencies',
        },
        component: CourseCompetenciesStudentPageComponent,
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisCompetenciesModule, FireworksModule],
    declarations: [CourseCompetenciesComponent],
    exports: [CourseCompetenciesComponent],
})
export class CourseCompetenciesModule {}
