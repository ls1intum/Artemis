import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const participationPaths: { path: string; mode: 'live' | 'practice' }[] = [
    { path: 'live', mode: 'live' },
    { path: 'practice', mode: 'practice' },
    { path: 'practice/:participationId', mode: 'practice' },
    { path: 'practice/:participationId/submission/:submissionId', mode: 'practice' },
];

export const routes: Routes = participationPaths.map(({ path, mode }) => ({
    path,
    loadComponent: () => import('./participation/quiz-participation.component').then((m) => m.QuizParticipationComponent),
    data: { authorities: [], pageTitle: 'overview.exercises', mode },
    canActivate: [UserRouteAccessService],
}));
