import { RouterModule, Routes } from '@angular/router';
import { ExerciseScoresComponent } from 'app/exercises/shared/exercise-scores/exercise-scores.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { ExerciseType, exerciseTypes } from 'app/entities/exercise.model';
import { ProfileToggle } from 'app/shared/profile-toggle/profile-toggle.service';
import { ProfileToggleGuard } from 'app/shared/profile-toggle/profile-toggle-guard.service';

const routes: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        const route: any = {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/scores',
            component: ExerciseScoresComponent,
            data: {
                authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                pageTitle: 'artemisApp.instructorDashboard.exerciseDashboard',
            },
            canActivate: [UserRouteAccessService],
        };
        if (exerciseType === ExerciseType.QUIZ) {
            route.canActivate.push(ProfileToggleGuard);
            route.data.profile = ProfileToggle.QUIZ;
        }
        return route;
    }),
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisExerciseScoresRoutingModule {}
