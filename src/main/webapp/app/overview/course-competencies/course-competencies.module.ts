import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseCompetenciesComponent } from 'app/overview/course-competencies/course-competencies.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { FireworksModule } from 'app/shared/fireworks/fireworks.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.competencies',
        },
        component: CourseCompetenciesComponent,
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisCompetenciesModule, FireworksModule],
    declarations: [CourseCompetenciesComponent],
    exports: [CourseCompetenciesComponent],
})
export class CourseCompetenciesModule {}
