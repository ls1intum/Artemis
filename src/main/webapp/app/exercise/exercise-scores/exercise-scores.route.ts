import { Routes } from '@angular/router';
import { ExerciseScoresComponent } from 'app/exercise/exercise-scores/exercise-scores.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';

export const routes: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/scores',
            component: ExerciseScoresComponent,
            data: {
                authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                pageTitle: 'artemisApp.instructorDashboard.exerciseDashboard',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
