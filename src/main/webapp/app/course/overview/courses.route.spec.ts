import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Route, Router, UrlSegment, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { courseRoutes, isTextUnitFullscreenUrl } from 'app/course/overview/courses.route';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

@Component({ template: '' })
class StubComponent {}

/** Keeps the shape of a route (paths, matchers, data, nesting) but drops guards and lazy loading, which need the whole application. */
function stub(route: Route): Route {
    const { canActivate, loadComponent, loadChildren, children, component, ...shape } = route;
    return {
        ...shape,
        ...(loadComponent || component ? { component: StubComponent } : {}),
        // lazy children are not needed to tell the lecture routes apart
        ...(children ? { children: children.map(stub) } : loadChildren && !loadComponent && !component ? { children: [] } : {}),
    };
}

describe('course routes', () => {
    const courseOverviewRoute = courseRoutes.find((route) => route.path === ':courseId');

    it('only provides the team detail page in the student course area', () => {
        const teamDetailRoute = courseOverviewRoute?.children?.find((route) => route.path === 'exercises/:exerciseId/teams/:teamId');

        expect(teamDetailRoute).toBeDefined();
        expect(teamDetailRoute?.data?.['authorities']).toBe(IS_AT_LEAST_STUDENT);
        expect(teamDetailRoute?.canActivate).toContain(UserRouteAccessService);
        expect(courseOverviewRoute?.children?.some((route) => route.path === 'exercises/:exerciseId/teams')).toBe(false);
    });

    describe('lectures', () => {
        const segments = (...paths: string[]) => paths.map((path) => new UrlSegment(path, {}));

        it('matches only the text unit full screen URL with its matcher', () => {
            const matches = (...paths: string[]) => isTextUnitFullscreenUrl({} as Route, segments(...paths), undefined as never);

            expect(matches('lectures', '5', 'text-units', '7')).toBe(true);
            expect(matches('lectures')).toBe(false);
            expect(matches('lectures', '5')).toBe(false);
            expect(matches('lectures', '5', 'text-units')).toBe(false);
            expect(matches('lectures', '5', 'something-else', '7')).toBe(false);
        });

        it.each([
            ['/courses/1/lectures', undefined, 'lectures'],
            ['/courses/1/lectures?x=y', undefined, 'lectures'],
            ['/courses/1/lectures/5', ':lectureId', 'lectures'],
            ['/courses/1/lectures/5?unit=7', ':lectureId', 'lectures'],
            ['/courses/1/lectures/5/text-units/7', ':lectureId/text-units/:unitId', 'lectures'],
        ])('routes %s to the expected page', async (url, expectedLeaf, expectedParent) => {
            TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'courses', children: [{ ...stub(courseOverviewRoute!), path: ':courseId' }] }])] });
            const router = TestBed.inject(Router);

            await router.navigateByUrl(url);

            const chain: ActivatedRouteSnapshot[] = [];
            for (let route: ActivatedRouteSnapshot | null = router.routerState.snapshot.root; route; route = route.firstChild) {
                chain.push(route);
            }
            const lecturesRoute = chain.find((route) => route.routeConfig?.path === expectedParent);
            const leaf = chain.at(-1)!;
            const isTextUnitFullscreen = expectedLeaf === ':lectureId/text-units/:unitId';

            expect(lecturesRoute).toBeDefined();
            expect(leaf.routeConfig?.path).toBe(expectedLeaf ?? expectedParent);
            // only the full screen page is rendered without the course sidebar and title bar
            expect(lecturesRoute!.data['isolatedView'] === true).toBe(isTextUnitFullscreen);
            // every other lecture URL ends on the page that renders the lecture list
            expect(lecturesRoute!.routeConfig?.component).toBe(isTextUnitFullscreen ? undefined : StubComponent);
        });
    });
});
