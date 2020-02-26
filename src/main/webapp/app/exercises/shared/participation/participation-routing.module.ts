import { RouterModule, Routes } from '@angular/router';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';

const routes: Routes = [
    {
        path: 'course-management/:courseId/exercises/:exerciseId/participations',
        component: ParticipationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisParticipationRoutingModule {}
