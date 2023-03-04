import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CourseRegistrationDetailComponent } from 'app/overview/course-registration/course-registration-detail/course-registration-detail.component';

const routes: Routes = [
    {
        path: '',
        component: CourseRegistrationDetailComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.register.signUpDetail',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, RouterModule.forChild(routes), ArtemisLearningGoalsModule],
    declarations: [CourseRegistrationDetailComponent],
})
export class CourseRegistrationDetailModule {}
