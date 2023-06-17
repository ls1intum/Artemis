import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseRegistrationButtonModule } from 'app/overview/course-registration/course-registration-button/course-registration-button.module';
import { CoursePrerequisitesButtonModule } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.module';

const routes: Routes = [
    {
        path: '',
        component: CourseRegistrationComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.enroll.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        RouterModule.forChild(routes),
        ArtemisCompetenciesModule,
        CourseRegistrationButtonModule,
        CoursePrerequisitesButtonModule,
    ],
    declarations: [CourseRegistrationComponent],
})
export class CourseRegistrationModule {}
