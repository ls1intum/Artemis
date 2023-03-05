import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CourseRegistrationDetailComponent } from 'app/overview/course-registration/course-registration-detail/course-registration-detail.component';
import { CoursePrerequisitesButtonModule } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.module';
import { CourseRegistrationButtonModule } from 'app/overview/course-registration/course-registration-button/course-registration-button.module';

const routes: Routes = [
    {
        path: '',
        component: CourseRegistrationDetailComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.register.signUp',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        RouterModule.forChild(routes),
        ArtemisLearningGoalsModule,
        CoursePrerequisitesButtonModule,
        CourseRegistrationButtonModule,
    ],
    declarations: [CourseRegistrationDetailComponent],
})
export class CourseRegistrationDetailModule {}
