import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisTutorialGroupsSharedModule } from 'app/course/tutorial-groups/shared/tutorial-groups-shared.module';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseTutorialGroupDetailComponent } from 'app/overview/tutorial-group-details/course-tutorial-group-detail/course-tutorial-group-detail.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const routes: Routes = [
    {
        path: '',
        loadComponent: () =>
            import('app/overview/tutorial-group-details/course-tutorial-group-detail/course-tutorial-group-detail.component').then((m) => m.CourseTutorialGroupDetailComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.pages.courseTutorialGroupDetail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisTutorialGroupsSharedModule, RouterModule.forChild(routes), ArtemisSharedModule, CourseTutorialGroupDetailComponent],
})
export class CourseTutorialGroupDetailsModule {}
