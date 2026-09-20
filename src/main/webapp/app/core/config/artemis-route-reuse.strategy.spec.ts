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

    it('should reuse route when params differ if dontReuseOnParamChange is not set (default Angular behavior)', () => {
        const curr = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
            data: {},
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig,
            params: { examId: '2', courseId: '10' },
            data: {},
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(true);
    });

    it('should not reuse route when params differ and dontReuseOnParamChange is true', () => {
        const curr = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
            data: { dontReuseOnParamChange: true },
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig,
            params: { examId: '2', courseId: '10' },
            data: { dontReuseOnParamChange: true },
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(false);
    });

    it('should not reuse route when params differ and parent route has dontReuseOnParamChange', () => {
        const parentSnapshot = {
            data: { dontReuseOnParamChange: true },
        } as unknown as ActivatedRouteSnapshot;
        const curr = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
            data: {},
            parent: parentSnapshot,
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig,
            params: { examId: '2', courseId: '10' },
            data: {},
            parent: parentSnapshot,
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(false);
    });

    it('should reuse route when route config and params are identical with dontReuseOnParamChange', () => {
        const curr = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
            data: { dontReuseOnParamChange: true },
        } as unknown as ActivatedRouteSnapshot;
        const future = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
            data: { dontReuseOnParamChange: true },
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(future, curr)).toBe(true);
    });

    it('should reuse parent route whose params are identical even if it has dontReuseOnParamChange', () => {
        const parentRouteConfig: Route = { path: '' };
        const currParent = {
            routeConfig: parentRouteConfig,
            params: { courseId: '10' },
            data: { dontReuseOnParamChange: true },
        } as unknown as ActivatedRouteSnapshot;
        const futureParent = {
            routeConfig: parentRouteConfig,
            params: { courseId: '10' },
            data: { dontReuseOnParamChange: true },
        } as unknown as ActivatedRouteSnapshot;

        // Parent component (e.g. ExamManagementComponent holding the sidebar) is reused
        expect(strategy.shouldReuseRoute(futureParent, currParent)).toBe(true);

        // While child component (e.g. exam subpage whose examId changed) is not reused
        const currChild = {
            routeConfig,
            params: { examId: '1', courseId: '10' },
            data: {},
            parent: currParent,
        } as unknown as ActivatedRouteSnapshot;
        const futureChild = {
            routeConfig,
            params: { examId: '2', courseId: '10' },
            data: {},
            parent: futureParent,
        } as unknown as ActivatedRouteSnapshot;

        expect(strategy.shouldReuseRoute(futureChild, currChild)).toBe(false);
    });
});
