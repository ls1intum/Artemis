import { RedirectFunction, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * The former scores page is the results view of the participation page. Old links and the score distribution
 * deep link (`scoreRangeFilter`) keep working. A relative redirect drops the query parameters it does not name,
 * so the one the page reads is passed on explicitly.
 */
export const scoresRedirect =
    (participationsPath: string): RedirectFunction =>
    ({ queryParams }) => {
        const scoreRangeFilter = queryParams['scoreRangeFilter'];
        return `${participationsPath}?view=results` + (scoreRangeFilter !== undefined ? `&scoreRangeFilter=${scoreRangeFilter}` : '');
    };

export const routes: Routes = [
    ...exerciseTypes.map((exerciseType) => ({
        path: exerciseType + '-exercises/:exerciseId/scores',
        redirectTo: scoresRedirect(exerciseType + '-exercises/:exerciseId/participations'),
    })),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/participations',
            loadComponent: () => import('./participation.component').then((m) => m.ParticipationComponent),
            data: {
                authorities: IS_AT_LEAST_TUTOR,
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: exerciseType + '-exercises/:exerciseId/participations/:participationId/submissions',
            loadComponent: () => import('../participation-submission/participation-submission.component').then((m) => m.ParticipationSubmissionComponent),
            data: {
                authorities: IS_AT_LEAST_INSTRUCTOR,
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];
