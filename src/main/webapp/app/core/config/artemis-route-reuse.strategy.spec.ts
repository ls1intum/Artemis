import { describe, expect, it } from 'vitest';
import { ActivatedRouteSnapshot, Route } from '@angular/router';
import { ArtemisRouteReuseStrategy } from 'app/core/config/artemis-route-reuse.strategy';

describe('ArtemisRouteReuseStrategy', () => {
    const strategy = new ArtemisRouteReuseStrategy();
    const routeConfig: Route = { path: ':examId/grading' };
    const otherRouteConfig: Route = { path: ':examId/scores' };

    it('should not reuse route when route configs differ', () => {
        const curr = {
            routeConfig,
            params: { examId: '1' },
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig: otherRouteConfig,
            params: { examId: '1' },
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(false);
    });

    it('should not reuse route when route params differ', () => {
        const curr = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig,
            params: { examId: '2', courseId: '10' },
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(false);
    });

    it('should reuse route when route config and params are identical', () => {
        const curr = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(true);
    });

    it('should reuse route when both have empty params', () => {
        const curr = {
            routeConfig,
            params: {},
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig,
            params: {},
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(true);
    });
});
