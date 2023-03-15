import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CoursePrerequisitesModalComponent } from 'app/overview/course-registration/course-prerequisites-modal.component';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const routes: Routes = [
    {
        path: '',
        component: CourseRegistrationComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.register.signUp',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, RouterModule.forChild(routes), ArtemisLearningGoalsModule],
    declarations: [CourseRegistrationComponent, CoursePrerequisitesModalComponent],
})
export class CourseRegistrationModule {}
