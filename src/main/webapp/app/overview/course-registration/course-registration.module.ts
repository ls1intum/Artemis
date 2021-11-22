import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CourseRegistrationSelectorComponent } from 'app/overview/course-registration/course-registration-selector/course-registration-selector.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

const routes: Routes = [
    {
        path: '',
        component: CourseRegistrationSelectorComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.registration',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, RouterModule.forChild(routes)],
    declarations: [CourseRegistrationSelectorComponent],
})
export class CourseRegistrationModule {}
