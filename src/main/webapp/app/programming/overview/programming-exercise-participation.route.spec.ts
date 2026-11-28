import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, RoutesRecognized, provideRouter } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { programmingExerciseParticipationRoute } from 'app/programming/overview/programming-exercise-participation.route';

describe('programmingExerciseParticipationRoute', () => {
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            // The guard refuses, so the navigation stops after the URL is recognised and the editor itself is never loaded
            providers: [provideRouter(programmingExerciseParticipationRoute), { provide: UserRouteAccessService, useValue: { canActivate: () => false } }],
        });
        router = TestBed.inject(Router);
    });

    const recognisedParams = async (url: string) => {
        const recognised = firstValueFrom(router.events.pipe(filter((event): event is RoutesRecognized => event instanceof RoutesRecognized)));
        void router.navigateByUrl(url);
        const event = await recognised;
        let route = event.state.root;
        while (route.firstChild) {
            route = route.firstChild;
        }
        return { path: route.routeConfig?.path, params: route.params };
    };

    it('should recognise the online editor of a participation', async () => {
        expect(await recognisedParams('/code-editor/21')).toEqual({ path: 'code-editor/:participationId', params: { participationId: '21' } });
    });

    it('should recognise an earlier submission and result opened from the result history', async () => {
        expect(await recognisedParams('/code-editor/21/submission/7/result/72')).toEqual({
            path: 'code-editor/:participationId/submission/:submissionId/result/:resultId',
            params: { participationId: '21', submissionId: '7', resultId: '72' },
        });
    });
});
