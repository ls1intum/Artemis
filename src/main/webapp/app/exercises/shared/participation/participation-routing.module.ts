import { RouterModule, Routes } from '@angular/router';
import { ParticipationComponent } from 'app/exercises/shared/participation/participation.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';

const routes: Routes = exerciseTypes.map((exerciseType) => {
    return {
        path: ':courseId/' + exerciseType + '-exercises/:exerciseId/participations',
        component: ParticipationComponent,
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    };
});

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisParticipationRoutingModule {}
