import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExerciseLtiConfigurationComponent } from './exercise-lti-configuration.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';

export const exercisePopupRoute: Routes = exerciseTypes.map((exerciseType) => {
    return {
        path: ':courseId/' + exerciseType + '-exercises/:exerciseId/lti-configuration',
        component: ExerciseLtiConfigurationComponent,
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    };
});
